package com.paymatch.review;

import com.paymatch.allocation.Allocation;
import com.paymatch.allocation.AllocationRepository;
import com.paymatch.allocation.AllocationService;
import com.paymatch.allocation.AllocationSource;
import com.paymatch.audit.AuditAction;
import com.paymatch.audit.AuditEntityType;
import com.paymatch.audit.AuditService;
import com.paymatch.common.Money;
import com.paymatch.common.error.ConflictException;
import com.paymatch.common.error.NotFoundException;
import com.paymatch.invoice.Invoice;
import com.paymatch.invoice.InvoiceRepository;
import com.paymatch.matching.MatchRule;
import com.paymatch.matching.MatchSuggestion;
import com.paymatch.matching.MatchSuggestionItem;
import com.paymatch.matching.MatchSuggestionRepository;
import com.paymatch.matching.MatchingService;
import com.paymatch.matching.SuggestionStatus;
import com.paymatch.transaction.BankTransaction;
import com.paymatch.transaction.BankTransactionRepository;
import com.paymatch.transaction.MatchStatus;
import com.paymatch.user.AppUser;
import com.paymatch.user.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Decisions a person makes in the review queue. Every method is one database transaction. */
@Service
public class ReviewService {

    /** Rules whose amount must still fit into what is left to pay; the others pay over the remainder on purpose. */
    private static final Set<MatchRule> MUST_FIT_REMAINING = Set.of(MatchRule.EXACT, MatchRule.PARTIAL_PAYMENT,
            MatchRule.MULTIPLE_INVOICES, MatchRule.COUNTERPARTY_AMOUNT, MatchRule.COMBINED_PAYMENT);

    private final BankTransactionRepository transactionRepository;
    private final MatchSuggestionRepository suggestionRepository;
    private final AllocationRepository allocationRepository;
    private final AllocationService allocationService;
    private final InvoiceRepository invoiceRepository;
    private final MatchingService matchingService;
    private final AuditService auditService;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final EntityManager entityManager;

    public ReviewService(BankTransactionRepository transactionRepository, MatchSuggestionRepository suggestionRepository,
                         AllocationRepository allocationRepository, AllocationService allocationService,
                         InvoiceRepository invoiceRepository, MatchingService matchingService, AuditService auditService,
                         CurrentUser currentUser, Clock clock, EntityManager entityManager) {
        this.transactionRepository = transactionRepository;
        this.suggestionRepository = suggestionRepository;
        this.allocationRepository = allocationRepository;
        this.allocationService = allocationService;
        this.invoiceRepository = invoiceRepository;
        this.matchingService = matchingService;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Transactional
    public void confirm(Long transactionId, ReviewRequests.Confirm request) {
        BankTransaction tx = loadForDecision(transactionId, request.version());
        MatchSuggestion suggestion = pendingSuggestion(tx, request.suggestionId());
        for (MatchSuggestionItem item : suggestion.getItems()) {
            Invoice invoice = item.getInvoice();
            if (MUST_FIT_REMAINING.contains(suggestion.getRule()) && item.getAmount().compareTo(invoice.getRemainingAmount()) > 0) {
                throw new ConflictException("Invoice " + invoice.getNumber() + " was paid in the meantime. "
                        + "Run matching again or match this payment manually.");
            }
        }

        AppUser user = currentUser.get();
        List<AllocationService.Part> parts = suggestion.getItems().stream()
                .map(item -> new AllocationService.Part(item.getInvoice().getId(), item.getAmount()))
                .toList();
        List<Allocation> allocations = allocationService.allocate(tx, parts, AllocationSource.MANUAL, suggestion, user);
        // the other suggestions are moot now; they come back if the match is undone
        suggestionRepository.deletePendingForTransactions(List.of(tx.getId()));

        auditService.record(AuditAction.MATCH_CONFIRMED, AuditEntityType.TRANSACTION, tx.getId(),
                "Confirmed " + suggestion.getRule().getLabel().toLowerCase() + ": " + describe(tx, allocations),
                invoiceIds(allocations), Map.of("rule", suggestion.getRule().name(), "score", suggestion.getScore()));
    }

    @Transactional
    public void reject(Long transactionId, ReviewRequests.Reject request) {
        BankTransaction tx = loadForDecision(transactionId, request.version());
        MatchSuggestion suggestion = pendingSuggestion(tx, request.suggestionId());
        suggestion.decide(SuggestionStatus.REJECTED, currentUser.get(), Instant.now(clock));

        boolean anyPendingLeft = suggestionRepository.findByTransactionIdOrderByScoreDesc(tx.getId()).stream()
                .anyMatch(s -> s.getStatus() == SuggestionStatus.PENDING);
        tx.setMatchStatus(anyPendingLeft ? MatchStatus.SUGGESTED : MatchStatus.UNMATCHED);

        String invoices = suggestion.getItems().stream().map(item -> item.getInvoice().getNumber()).collect(Collectors.joining(", "));
        auditService.record(AuditAction.SUGGESTION_REJECTED, AuditEntityType.TRANSACTION, tx.getId(),
                "Rejected suggestion " + invoices + " (" + suggestion.getRule().getLabel().toLowerCase() + ") for payment of "
                        + Money.format(tx.getAmount(), tx.getCurrency()),
                suggestion.getItems().stream().map(item -> item.getInvoice().getId()).toList(),
                Map.of("rule", suggestion.getRule().name()));
    }

    /** Picking a different invoice, or splitting one payment across several invoices. */
    @Transactional
    public void allocateManually(Long transactionId, ReviewRequests.Allocate request) {
        BankTransaction tx = loadForDecision(transactionId, request.version());
        Set<Long> seen = new HashSet<>();
        for (ReviewRequests.Part part : request.parts()) {
            if (!seen.add(part.invoiceId())) {
                throw new ConflictException("The same invoice appears twice; put the whole amount in one line");
            }
            if (!invoiceRepository.existsById(part.invoiceId())) {
                throw NotFoundException.of("Invoice", part.invoiceId());
            }
        }

        List<AllocationService.Part> parts = request.parts().stream()
                .map(part -> new AllocationService.Part(part.invoiceId(), Money.of(part.amount())))
                .toList();
        List<Allocation> allocations = allocationService.allocate(tx, parts, AllocationSource.MANUAL, null, currentUser.get());
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        allocations.forEach(allocation -> allocation.setNote(note));
        suggestionRepository.deletePendingForTransactions(List.of(tx.getId()));

        String summary = allocations.size() == 1
                ? "Matched manually: " + describe(tx, allocations)
                : "Split payment: " + describe(tx, allocations);
        auditService.record(AuditAction.MANUAL_ALLOCATION, AuditEntityType.TRANSACTION, tx.getId(), summary,
                invoiceIds(allocations), note == null ? Map.of() : Map.of("note", note));
    }

    @Transactional
    public void ignore(Long transactionId, ReviewRequests.Ignore request) {
        BankTransaction tx = loadForDecision(transactionId, request.version());
        if (tx.getMatchStatus() == MatchStatus.MATCHED) {
            throw new ConflictException("Payment is matched to an invoice; undo the match first");
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        tx.ignore(request.reason(), note);
        suggestionRepository.deletePendingForTransactions(List.of(tx.getId()));
        auditService.record(AuditAction.TRANSACTION_IGNORED, AuditEntityType.TRANSACTION, tx.getId(),
                "Marked payment of " + Money.format(tx.getAmount(), tx.getCurrency()) + " as not an invoice payment: "
                        + request.reason().getLabel().toLowerCase(),
                List.of(), note == null ? Map.of("reason", request.reason().name()) : Map.of("reason", request.reason().name(), "note", note));
    }

    /**
     * Reverts a confirmed match (manual or automatic) or an "ignore" decision. The payment goes back to the queue
     * with fresh suggestions, but nothing is auto-confirmed again: someone just decided that it needs a look.
     */
    @Transactional
    public void undo(Long transactionId, ReviewRequests.Undo request) {
        BankTransaction tx = loadForDecision(transactionId, request.version());
        String summary;
        List<Long> invoiceIds;
        if (tx.getMatchStatus() == MatchStatus.MATCHED) {
            List<Allocation> allocations = allocationRepository.findByTransactionIdOrderById(tx.getId());
            List<Invoice> invoices = allocations.stream().map(Allocation::getInvoice).distinct().toList();
            summary = "Undid match: " + describe(tx, allocations);
            invoiceIds = invoiceIds(allocations);
            allocationRepository.deleteAll(allocations);
            allocationService.recalculate(invoices);
            suggestionRepository.deleteDecidedNonRejected(tx.getId());
        } else if (tx.getMatchStatus() == MatchStatus.IGNORED) {
            summary = "Payment of " + Money.format(tx.getAmount(), tx.getCurrency()) + " is back in the review queue (was: "
                    + tx.getIgnoreReason().getLabel().toLowerCase() + ")";
            invoiceIds = List.of();
        } else {
            throw new ConflictException("There is no decision to undo for this payment");
        }
        tx.reopen();
        auditService.record(AuditAction.MATCH_UNDONE, AuditEntityType.TRANSACTION, tx.getId(), summary, invoiceIds, Map.of());
        matchingService.rematchWithoutAutoConfirm(List.of(tx));
    }

    /** Locks the transaction row and checks that the person decided on the current state of it. */
    private BankTransaction loadForDecision(Long id, long expectedVersion) {
        BankTransaction tx = transactionRepository.findForDecision(id).orElseThrow(() -> NotFoundException.of("Transaction", id));
        if (tx.getVersion() != expectedVersion) {
            throw new ConflictException("This payment was changed by someone else in the meantime. Reload and check it again.");
        }
        if (!tx.isIncoming()) {
            throw new ConflictException("Outgoing transactions are not matched to invoices");
        }
        // bump the version on commit even when only a suggestion changes, so every open screen becomes stale
        entityManager.lock(tx, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        return tx;
    }

    private MatchSuggestion pendingSuggestion(BankTransaction tx, Long suggestionId) {
        MatchSuggestion suggestion = suggestionRepository.findById(suggestionId)
                .filter(s -> s.getTransaction().getId().equals(tx.getId()))
                .orElseThrow(() -> NotFoundException.of("Suggestion", suggestionId));
        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            throw new ConflictException("This suggestion was already " + suggestion.getStatus().name().toLowerCase());
        }
        return suggestion;
    }

    private static String describe(BankTransaction tx, List<Allocation> allocations) {
        String parts = allocations.stream()
                .map(a -> a.getInvoice().getNumber() + " (" + Money.format(a.getAmount(), tx.getCurrency()) + ")")
                .collect(Collectors.joining(", "));
        String from = tx.getCounterpartyName() != null ? " from " + tx.getCounterpartyName() : "";
        return Money.format(tx.getAmount(), tx.getCurrency()) + from + " → " + parts;
    }

    private static List<Long> invoiceIds(List<Allocation> allocations) {
        return allocations.stream().map(a -> a.getInvoice().getId()).distinct().toList();
    }
}
