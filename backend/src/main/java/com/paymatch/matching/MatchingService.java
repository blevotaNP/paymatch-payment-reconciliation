package com.paymatch.matching;

import com.paymatch.allocation.AllocationService;
import com.paymatch.allocation.AllocationSource;
import com.paymatch.audit.AuditAction;
import com.paymatch.audit.AuditEntityType;
import com.paymatch.audit.AuditService;
import com.paymatch.common.Money;
import com.paymatch.counterparty.CounterpartyRepository;
import com.paymatch.invoice.InvoiceRepository;
import com.paymatch.transaction.BankTransaction;
import com.paymatch.transaction.BankTransactionRepository;
import com.paymatch.transaction.MatchStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    private final MatchingEngine engine;
    private final MatchingProperties properties;
    private final MatchSuggestionRepository suggestionRepository;
    private final BankTransactionRepository transactionRepository;
    private final InvoiceRepository invoiceRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final AllocationService allocationService;
    private final AuditService auditService;

    public MatchingService(MatchingEngine engine, MatchingProperties properties, MatchSuggestionRepository suggestionRepository,
                           BankTransactionRepository transactionRepository, InvoiceRepository invoiceRepository,
                           CounterpartyRepository counterpartyRepository, AllocationService allocationService,
                           AuditService auditService) {
        this.engine = engine;
        this.properties = properties;
        this.suggestionRepository = suggestionRepository;
        this.transactionRepository = transactionRepository;
        this.invoiceRepository = invoiceRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.allocationService = allocationService;
        this.auditService = auditService;
    }

    /** Matches transactions that were just imported. */
    @Transactional
    public MatchingRunResult matchTransactions(Collection<BankTransaction> transactions) {
        return run(transactions, properties.autoConfirmExact());
    }

    /** Fresh suggestions only; used after a person undid a match, so the engine does not redo it behind their back. */
    @Transactional
    public MatchingRunResult rematchWithoutAutoConfirm(Collection<BankTransaction> transactions) {
        return run(transactions, false);
    }

    private MatchingRunResult run(Collection<BankTransaction> transactions, boolean autoConfirm) {
        List<BankTransaction> toMatch = transactions.stream()
                .filter(BankTransaction::isIncoming)
                .filter(tx -> tx.getMatchStatus() == MatchStatus.UNMATCHED || tx.getMatchStatus() == MatchStatus.SUGGESTED)
                .sorted(Comparator.comparing(BankTransaction::getBookingDate).thenComparing(BankTransaction::getId))
                .toList();
        if (toMatch.isEmpty()) {
            return MatchingRunResult.empty();
        }
        List<Long> ids = toMatch.stream().map(BankTransaction::getId).toList();
        suggestionRepository.deletePendingForTransactions(ids);
        Map<Long, Set<String>> rejected = rejectedKeys(ids);

        MatchingContext context = loadContext();
        int autoConfirmed = 0;
        int suggested = 0;
        for (BankTransaction tx : toMatch) {
            List<MatchCandidate> candidates = engine.suggest(tx, context, rejected.getOrDefault(tx.getId(), Set.of()));
            List<MatchSuggestion> saved = candidates.stream().map(candidate -> save(tx, candidate)).toList();

            Optional<MatchCandidate> auto = autoConfirm ? engine.autoConfirmable(candidates) : Optional.empty();
            if (auto.isPresent()) {
                MatchCandidate candidate = auto.get();
                List<AllocationService.Part> parts = candidate.items().stream()
                        .map(item -> new AllocationService.Part(item.invoice().getId(), item.amount()))
                        .toList();
                allocationService.allocate(tx, parts, AllocationSource.AUTO, saved.getFirst(), null);
                auditService.recordSystem(AuditAction.MATCH_AUTO_CONFIRMED, AuditEntityType.TRANSACTION, tx.getId(),
                        "Payment of " + Money.format(tx.getAmount(), tx.getCurrency()) + " matched automatically to "
                                + candidate.items().getFirst().invoice().getNumber(),
                        candidate.items().stream().map(item -> item.invoice().getId()).toList(),
                        Map.of("rule", candidate.rule().name(), "score", candidate.score()));
                // later transactions in this run must see the invoice as paid
                candidate.items().forEach(item -> item.invoice().addPaid(item.amount()));
                autoConfirmed++;
            } else {
                tx.setMatchStatus(candidates.isEmpty() ? MatchStatus.UNMATCHED : MatchStatus.SUGGESTED);
                if (!candidates.isEmpty()) {
                    suggested++;
                }
            }
        }
        log.info("Matching run: {} checked, {} auto-confirmed, {} with suggestions", toMatch.size(), autoConfirmed, suggested);
        return new MatchingRunResult(toMatch.size(), autoConfirmed, suggested, toMatch.size() - autoConfirmed - suggested);
    }

    /** Re-evaluates every incoming transaction that is not decided yet, e.g. after new invoices were added. */
    @Transactional
    public MatchingRunResult rematchOpenTransactions() {
        return matchTransactions(transactionRepository.findByMatchStatusIn(List.of(MatchStatus.UNMATCHED, MatchStatus.SUGGESTED)));
    }

    @Transactional
    public MatchingRunResult rematch(Collection<Long> transactionIds) {
        return matchTransactions(transactionRepository.findAllById(transactionIds));
    }

    private MatchSuggestion save(BankTransaction tx, MatchCandidate candidate) {
        MatchSuggestion suggestion = new MatchSuggestion(tx, candidate.rule(), candidate.score(), candidate.reasons());
        for (MatchCandidate.Item item : candidate.items()) {
            suggestion.addItem(new MatchSuggestionItem(suggestion, invoiceRepository.getReferenceById(item.invoice().getId()), item.amount()));
        }
        return suggestionRepository.save(suggestion);
    }

    private Map<Long, Set<String>> rejectedKeys(List<Long> transactionIds) {
        Map<Long, Set<String>> keys = new HashMap<>();
        for (MatchSuggestion rejected : suggestionRepository.findRejectedForTransactions(transactionIds)) {
            String key = rejected.getItems().stream().map(item -> item.getInvoice().getId()).sorted()
                    .map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
            keys.computeIfAbsent(rejected.getTransaction().getId(), id -> new HashSet<>()).add(key);
        }
        return keys;
    }

    private MatchingContext loadContext() {
        List<InvoiceSnapshot> invoices = invoiceRepository.findAllWithCounterparty().stream()
                .map(invoice -> new InvoiceSnapshot(invoice.getId(), invoice.getNumber(), invoice.getCounterparty().getId(),
                        invoice.getCounterparty().getName(), invoice.getCurrency(), invoice.getGrossAmount(),
                        invoice.getPaidAmount(), invoice.getDueDate()))
                .toList();
        List<CounterpartySnapshot> counterparties = counterpartyRepository.findAll().stream()
                .map(c -> new CounterpartySnapshot(c.getId(), c.getName(), c.getNip(), c.getIban()))
                .toList();
        return new MatchingContext(invoices, counterparties);
    }
}
