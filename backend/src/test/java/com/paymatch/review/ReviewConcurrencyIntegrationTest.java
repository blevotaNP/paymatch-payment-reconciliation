package com.paymatch.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymatch.DatabaseCleaner;
import com.paymatch.IntegrationTest;
import com.paymatch.allocation.AllocationRepository;
import com.paymatch.allocation.AllocationService;
import com.paymatch.allocation.AllocationSource;
import com.paymatch.common.error.ConflictException;
import com.paymatch.invoice.Invoice;
import com.paymatch.invoice.InvoiceRepository;
import com.paymatch.matching.MatchSuggestionRepository;
import com.paymatch.transaction.BankTransaction;
import com.paymatch.transaction.BankTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two people deciding at the same moment. The barrier makes both threads read the data before either
 * writes, which is exactly the situation where a naive "read balance, add, save" would corrupt it.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "anna")
class ReviewConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DatabaseCleaner databaseCleaner;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private BankTransactionRepository transactionRepository;
    @Autowired
    private MatchSuggestionRepository suggestionRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private AllocationService allocationService;
    @Autowired
    private ReviewService reviewService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private ReviewTestSupport data;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @BeforeEach
    void setUp() throws Exception {
        databaseCleaner.clean();
        data = new ReviewTestSupport(mockMvc, objectMapper, invoiceRepository, transactionRepository, suggestionRepository);
        data.importInvoices();
        data.importStatement("mt940_pekao_2026-09.sta");
        data.importStatement("mt940_pekao_2026-09-25_2026-10-10.sta");
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void twoTransfersPayingTheSameInvoiceAtOnceCannotBothSucceed() throws Exception {
        // both Wiśniewski transfers (2 000 and 2 920) are allocated in full to FV/2/09/2026 by two people at once
        Long invoiceId = data.invoice("FV/2/09/2026").getId();
        Long first = data.transaction("Zaplata za fv 2/09/2026 czesc 1").getId();
        Long second = data.transaction("FV/2/09/2026 doplata").getId();
        CyclicBarrier bothLoaded = new CyclicBarrier(2);

        List<Throwable> failures = runConcurrently(
                () -> allocateAfterBarrier(first, invoiceId, new BigDecimal("2000.00"), bothLoaded),
                () -> allocateAfterBarrier(second, invoiceId, new BigDecimal("2920.00"), bothLoaded));

        assertThat(failures).singleElement().isInstanceOf(OptimisticLockingFailureException.class);
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
        assertThat(invoice.getPaidAmount())
                .as("paid amount equals the one allocation that was committed")
                .isEqualByComparingTo(allocationRepository.sumForInvoice(invoiceId));
        assertThat(invoice.getPaidAmount()).isIn(new BigDecimal("2000.00"), new BigDecimal("2920.00"));
    }

    @Test
    void oneTransferMatchedToTwoDifferentInvoicesAtOnceKeepsOnlyOne() throws Exception {
        // Przychodnia's 12 300 could be FV/3/09 or FV/3/10; two people pick different ones simultaneously
        Long tx = data.transaction("Faktura za wrzesien").getId();
        Long september = data.invoice("FV/3/09/2026").getId();
        Long october = data.invoice("FV/3/10/2026").getId();
        CyclicBarrier bothLoaded = new CyclicBarrier(2);

        List<Throwable> failures = runConcurrently(
                () -> allocateAfterBarrier(tx, september, new BigDecimal("12300.00"), bothLoaded),
                () -> allocateAfterBarrier(tx, october, new BigDecimal("12300.00"), bothLoaded));

        assertThat(failures).singleElement().isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(allocationRepository.findByTransactionIdOrderById(tx)).hasSize(1);
        BigDecimal paidTotal = invoiceRepository.findById(september).orElseThrow().getPaidAmount()
                .add(invoiceRepository.findById(october).orElseThrow().getPaidAmount());
        assertThat(paidTotal).isEqualByComparingTo("12300.00");
    }

    @Test
    void twoPeopleConfirmingDifferentSuggestionsThroughTheServiceGetOneSuccessAndOneConflict() throws Exception {
        BankTransaction tx = data.transaction("Faktura za wrzesien");
        Long septemberSuggestion = data.pendingSuggestion(tx.getTitle(), "FV/3/09/2026").getId();
        Long octoberSuggestion = data.pendingSuggestion(tx.getTitle(), "FV/3/10/2026").getId();
        CyclicBarrier start = new CyclicBarrier(2);

        List<Throwable> failures = runConcurrently(
                () -> confirmAs("anna", tx, septemberSuggestion, start),
                () -> confirmAs("piotr", tx, octoberSuggestion, start));

        // the second one waits for the row lock, then sees the version the first one left behind
        assertThat(failures).singleElement().isInstanceOf(ConflictException.class);
        assertThat(failures.getFirst()).hasMessageContaining("changed by someone else");
        assertThat(allocationRepository.findByTransactionIdOrderById(tx.getId())).hasSize(1);
    }

    private void allocateAfterBarrier(Long transactionId, Long invoiceId, BigDecimal amount, CyclicBarrier barrier) {
        transactionTemplate.executeWithoutResult(status -> {
            BankTransaction tx = transactionRepository.findById(transactionId).orElseThrow();
            invoiceRepository.findById(invoiceId).orElseThrow();
            await(barrier);
            allocationService.allocate(tx, List.of(new AllocationService.Part(invoiceId, amount)), AllocationSource.MANUAL, null, null);
        });
    }

    private void confirmAs(String username, BankTransaction tx, Long suggestionId, CyclicBarrier barrier) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, null, List.of()));
        try {
            await(barrier);
            reviewService.confirm(tx.getId(), new ReviewRequests.Confirm(suggestionId, tx.getVersion()));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private List<Throwable> runConcurrently(Runnable a, Runnable b) throws Exception {
        List<Future<?>> futures = List.of(executor.submit(a), executor.submit(b));
        List<Throwable> failures = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException e) {
                failures.add(e.getCause());
            }
        }
        return failures;
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
