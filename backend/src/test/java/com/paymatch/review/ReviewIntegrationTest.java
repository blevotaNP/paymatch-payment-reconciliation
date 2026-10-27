package com.paymatch.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymatch.DatabaseCleaner;
import com.paymatch.IntegrationTest;
import com.paymatch.audit.AuditAction;
import com.paymatch.audit.AuditEventRepository;
import com.paymatch.invoice.InvoiceRepository;
import com.paymatch.invoice.InvoiceStatus;
import com.paymatch.matching.MatchSuggestionRepository;
import com.paymatch.matching.SuggestionStatus;
import com.paymatch.transaction.BankTransaction;
import com.paymatch.transaction.BankTransactionRepository;
import com.paymatch.transaction.MatchStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "anna")
class ReviewIntegrationTest {

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
    private AuditEventRepository auditRepository;

    private ReviewTestSupport data;

    @BeforeEach
    void setUp() throws Exception {
        databaseCleaner.clean();
        data = new ReviewTestSupport(mockMvc, objectMapper, invoiceRepository, transactionRepository, suggestionRepository);
        data.importInvoices();
    }

    @Test
    void confirmingPartialPaymentUpdatesInvoiceAndWritesAudit() throws Exception {
        data.importStatement("mt940_pekao_2026-09.sta");
        BankTransaction tx = data.transaction("Zaplata za fv 2/09/2026 czesc 1");
        Long suggestionId = data.pendingSuggestion(tx.getTitle(), "FV/2/09/2026").getId();

        action(tx.getId(), "confirm", """
                {"suggestionId": %d, "version": %d}""".formatted(suggestionId, tx.getVersion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.matchStatus").value("MATCHED"))
                .andExpect(jsonPath("$.transaction.version").value(greaterThan((int) tx.getVersion())))
                .andExpect(jsonPath("$.allocations[0].invoiceNumber").value("FV/2/09/2026"))
                .andExpect(jsonPath("$.allocations[0].source").value("MANUAL"))
                .andExpect(jsonPath("$.allocations[0].createdBy").value("Anna Kowalska"));

        assertThat(data.invoice("FV/2/09/2026").getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
        assertThat(data.invoice("FV/2/09/2026").getPaidAmount()).isEqualByComparingTo("2000.00");
        mockMvc.perform(get("/api/audit").param("action", "MATCH_CONFIRMED"))
                .andExpect(jsonPath("$.content[0].user").value("Anna Kowalska"))
                .andExpect(jsonPath("$.content[0].summary").value(containsString("FV/2/09/2026")));
    }

    @Test
    void decisionOnOutdatedScreenIsRefused() throws Exception {
        data.importStatement("mt940_pekao_2026-09.sta");
        BankTransaction tx = data.transaction("Zaplata za fv 2/09/2026 czesc 1");
        Long suggestionId = data.pendingSuggestion(tx.getTitle(), "FV/2/09/2026").getId();

        action(tx.getId(), "confirm", """
                {"suggestionId": %d, "version": %d}""".formatted(suggestionId, tx.getVersion() + 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("changed by someone else")));

        assertThat(data.invoice("FV/2/09/2026").getStatus()).isEqualTo(InvoiceStatus.UNPAID);
    }

    @Test
    void rejectedSuggestionIsNotOfferedAgain() throws Exception {
        data.importStatement("pko_bp_historia_2026-10.csv");
        BankTransaction tx = data.transaction("zaplata za fakture");
        Long suggestionId = data.pendingSuggestion(tx.getTitle(), "FV/5/09/2026").getId();

        action(tx.getId(), "reject", """
                {"suggestionId": %d, "version": %d}""".formatted(suggestionId, tx.getVersion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.matchStatus").value("UNMATCHED"))
                .andExpect(jsonPath("$.suggestions[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.suggestions[0].decidedBy").value("Anna Kowalska"));

        mockMvc.perform(post("/api/matching/run").with(csrf())).andExpect(status().isOk());

        assertThat(data.transaction("zaplata za fakture").getMatchStatus()).isEqualTo(MatchStatus.UNMATCHED);
        assertThat(suggestionRepository.findByTransactionIdOrderByScoreDesc(tx.getId()))
                .singleElement().extracting(s -> s.getStatus()).isEqualTo(SuggestionStatus.REJECTED);
    }

    @Test
    void splittingCombinedPaymentPaysBothInvoices() throws Exception {
        data.importStatement("mbank_lista_operacji_2026-10.csv");
        BankTransaction tx = data.transaction("Platnosc za faktury");
        Long first = data.invoice("FV/5/10/2026").getId();
        Long second = data.invoice("FV/10/10/2026").getId();

        action(tx.getId(), "allocate", """
                {"parts": [{"invoiceId": %d, "amount": "3075.00"}, {"invoiceId": %d, "amount": "1537.50"}],
                 "note": "Client confirmed by phone", "version": %d}""".formatted(first, second, tx.getVersion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocations.length()").value(2))
                .andExpect(jsonPath("$.allocations[0].note").value("Client confirmed by phone"))
                .andExpect(jsonPath("$.suggestions.length()").value(0));

        assertThat(data.invoice("FV/5/10/2026").getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(data.invoice("FV/10/10/2026").getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(auditRepository.findAll()).anyMatch(e -> e.getAction() == AuditAction.MANUAL_ALLOCATION
                && e.getSummary().startsWith("Split payment") && e.getInvoiceIds().length == 2);
    }

    @Test
    void manualAllocationMustAddUpAndBeValid() throws Exception {
        data.importStatement("mbank_lista_operacji_2026-10.csv");
        BankTransaction tx = data.transaction("Platnosc za faktury");
        Long invoice = data.invoice("FV/5/10/2026").getId();

        action(tx.getId(), "allocate", """
                {"parts": [{"invoiceId": %d, "amount": "3075.00"}], "version": %d}""".formatted(invoice, tx.getVersion()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Allocated amounts must add up to the transaction amount"));

        action(tx.getId(), "allocate", """
                {"parts": [{"invoiceId": %d, "amount": "3000.00"}, {"invoiceId": %d, "amount": "1612.50"}], "version": %d}"""
                .formatted(invoice, invoice, tx.getVersion()))
                .andExpect(status().isConflict());

        action(tx.getId(), "allocate", """
                {"parts": [{"invoiceId": %d, "amount": "-4612.50"}], "version": %d}""".formatted(invoice, tx.getVersion()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("parts[0].amount"));

        assertThat(data.invoice("FV/5/10/2026").getPaidAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void ignoringRefundBlocksUndoingTheWholeImport() throws Exception {
        long importId = data.importStatement("mbank_lista_operacji_2026-10.csv").get("id").asLong();
        BankTransaction refund = data.transaction("Zwrot nadplaty za usluge hostingowa");

        action(refund.getId(), "ignore", """
                {"reason": "REFUND", "note": "Hosting overpaid in September", "version": %d}""".formatted(refund.getVersion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.matchStatus").value("IGNORED"))
                .andExpect(jsonPath("$.transaction.ignoreReasonLabel").value("Refund from a supplier"));

        mockMvc.perform(post("/api/imports/{id}/undo", importId).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("reviewed by a person")));
    }

    @Test
    void undoingAutomaticMatchReopensPaymentWithoutAutoConfirmingAgain() throws Exception {
        data.importStatement("mt940_pekao_2026-09.sta");
        BankTransaction tx = data.transaction("FV/1/09/2026");
        assertThat(tx.getMatchStatus()).isEqualTo(MatchStatus.MATCHED);

        action(tx.getId(), "undo", """
                {"version": %d}""".formatted(tx.getVersion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.matchStatus").value("SUGGESTED"))
                .andExpect(jsonPath("$.allocations.length()").value(0))
                .andExpect(jsonPath("$.suggestions[0].rule").value("EXACT"))
                .andExpect(jsonPath("$.suggestions[0].status").value("PENDING"));

        assertThat(data.invoice("FV/1/09/2026").getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(auditRepository.findAll()).anyMatch(e -> e.getAction() == AuditAction.MATCH_UNDONE);
    }

    @Test
    void undoingIgnoreSendsPaymentBackToTheQueue() throws Exception {
        data.importStatement("mbank_lista_operacji_2026-10.csv");
        BankTransaction refund = data.transaction("Zwrot nadplaty za usluge hostingowa");
        action(refund.getId(), "ignore", """
                {"reason": "REFUND", "version": %d}""".formatted(refund.getVersion())).andExpect(status().isOk());

        BankTransaction ignored = data.transaction("Zwrot nadplaty za usluge hostingowa");
        action(ignored.getId(), "undo", """
                {"version": %d}""".formatted(ignored.getVersion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.matchStatus").value("UNMATCHED"))
                .andExpect(jsonPath("$.transaction.ignoreReason").doesNotExist());
    }

    @Test
    void confirmingSuggestionForInvoicePaidInTheMeantimeIsRefused() throws Exception {
        data.importStatement("mt940_pekao_2026-09.sta");
        data.importStatement("mt940_pekao_2026-09-25_2026-10-10.sta");
        BankTransaction rest = data.transaction("FV/2/09/2026 doplata");
        Long staleSuggestion = data.pendingSuggestion(rest.getTitle(), "FV/2/09/2026").getId();

        // someone else pays FV/2/09/2026 in full from another transfer
        BankTransaction other = data.transaction("Faktura za wrzesien");
        action(other.getId(), "allocate", """
                {"parts": [{"invoiceId": %d, "amount": "4920.00"}, {"invoiceId": %d, "amount": "7380.00"}], "version": %d}"""
                .formatted(data.invoice("FV/2/09/2026").getId(), data.invoice("FV/3/09/2026").getId(), other.getVersion()))
                .andExpect(status().isOk());

        action(rest.getId(), "confirm", """
                {"suggestionId": %d, "version": %d}""".formatted(staleSuggestion, rest.getVersion()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("paid in the meantime")));

        assertThat(data.invoice("FV/2/09/2026").getPaidAmount()).isEqualByComparingTo("4920.00");
    }

    @Test
    void outgoingTransactionsCannotBeMatched() throws Exception {
        data.importStatement("mt940_pekao_2026-09.sta");
        BankTransaction rent = data.transaction("Czynsz za biuro 10/2026");

        action(rent.getId(), "ignore", """
                {"reason": "OTHER", "version": %d}""".formatted(rent.getVersion()))
                .andExpect(status().isConflict());
    }

    @Test
    void historyIsAvailablePerTransactionAndInvoice() throws Exception {
        data.importStatement("mt940_pekao_2026-09.sta");
        BankTransaction tx = data.transaction("FV/1/09/2026");
        Long invoiceId = data.invoice("FV/1/09/2026").getId();

        mockMvc.perform(get("/api/audit/transactions/{id}", tx.getId()))
                .andExpect(jsonPath("$[0].action").value("MATCH_AUTO_CONFIRMED"))
                .andExpect(jsonPath("$[0].user").doesNotExist());
        mockMvc.perform(get("/api/audit/invoices/{id}", invoiceId))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action").value("MATCH_AUTO_CONFIRMED"));
        mockMvc.perform(get("/api/invoices/{id}/payments", invoiceId))
                .andExpect(jsonPath("$[0].amount").value("1230.50"))
                .andExpect(jsonPath("$[0].source").value("AUTO"));
        mockMvc.perform(get("/api/review/summary"))
                .andExpect(jsonPath("$.toReview").value(1));
    }

    private ResultActions action(Long transactionId, String action, String body) throws Exception {
        return mockMvc.perform(post("/api/review/transactions/{id}/" + action, transactionId).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
