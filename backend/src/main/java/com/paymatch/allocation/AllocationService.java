package com.paymatch.allocation;

import com.paymatch.common.error.ConflictException;
import com.paymatch.common.error.NotFoundException;
import com.paymatch.invoice.Invoice;
import com.paymatch.invoice.InvoiceRepository;
import com.paymatch.matching.MatchSuggestion;
import com.paymatch.matching.SuggestionStatus;
import com.paymatch.transaction.BankTransaction;
import com.paymatch.transaction.MatchStatus;
import com.paymatch.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * The only place where money gets assigned to invoices. Paid amount is always recalculated from the stored
 * allocations (never incremented), and the invoice update bumps its version, so two conflicting confirmations
 * running at the same time cannot both succeed.
 */
@Service
public class AllocationService {

    private final AllocationRepository allocationRepository;
    private final InvoiceRepository invoiceRepository;
    private final Clock clock;

    public AllocationService(AllocationRepository allocationRepository, InvoiceRepository invoiceRepository, Clock clock) {
        this.allocationRepository = allocationRepository;
        this.invoiceRepository = invoiceRepository;
        this.clock = clock;
    }

    public record Part(Long invoiceId, BigDecimal amount) {
    }

    /** Must run inside the caller's transaction: allocation, invoice update and status change commit together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Allocation> allocate(BankTransaction tx, List<Part> parts, AllocationSource source,
                                     MatchSuggestion suggestion, AppUser user) {
        if (!tx.isIncoming()) {
            throw new ConflictException("Only incoming transactions can be matched to invoices");
        }
        if (tx.getMatchStatus() == MatchStatus.MATCHED || tx.getMatchStatus() == MatchStatus.IGNORED) {
            throw new ConflictException("Transaction is already " + tx.getMatchStatus().name().toLowerCase());
        }
        BigDecimal total = parts.stream().map(Part::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (parts.isEmpty() || parts.stream().anyMatch(part -> part.amount().signum() <= 0)) {
            throw new ConflictException("Every allocated amount must be greater than zero");
        }
        if (total.compareTo(tx.getAmount()) != 0) {
            throw new ConflictException("Allocated amounts must add up to the transaction amount");
        }

        List<Allocation> allocations = parts.stream().map(part -> {
            Invoice invoice = invoiceRepository.findById(part.invoiceId())
                    .orElseThrow(() -> NotFoundException.of("Invoice", part.invoiceId()));
            if (!invoice.getCurrency().equals(tx.getCurrency())) {
                throw new ConflictException("Invoice " + invoice.getNumber() + " is in " + invoice.getCurrency()
                        + ", the transaction in " + tx.getCurrency());
            }
            return allocationRepository.save(new Allocation(tx, invoice, part.amount(), source, suggestion, user));
        }).toList();

        recalculate(allocations.stream().map(Allocation::getInvoice).distinct().toList());
        tx.setMatchStatus(MatchStatus.MATCHED);
        if (suggestion != null) {
            suggestion.decide(SuggestionStatus.CONFIRMED, user, Instant.now(clock));
        }
        return allocations;
    }

    /** Recomputes paid amount and status from the allocations currently stored for each invoice. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recalculate(Collection<Invoice> invoices) {
        allocationRepository.flush();
        for (Invoice invoice : invoices) {
            invoice.applyPaidAmount(allocationRepository.sumForInvoice(invoice.getId()));
        }
    }
}
