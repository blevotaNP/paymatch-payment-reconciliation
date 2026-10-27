package com.paymatch.transaction;

import com.paymatch.allocation.Allocation;
import com.paymatch.allocation.AllocationRepository;
import com.paymatch.allocation.AllocationResponse;
import com.paymatch.common.error.NotFoundException;
import com.paymatch.matching.MatchSuggestion;
import com.paymatch.matching.MatchSuggestionRepository;
import com.paymatch.matching.SuggestionResponse;
import com.paymatch.transaction.TransactionResponse.MatchSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final BankTransactionRepository transactionRepository;
    private final MatchSuggestionRepository suggestionRepository;
    private final AllocationRepository allocationRepository;

    public TransactionService(BankTransactionRepository transactionRepository, MatchSuggestionRepository suggestionRepository,
                              AllocationRepository allocationRepository) {
        this.transactionRepository = transactionRepository;
        this.suggestionRepository = suggestionRepository;
        this.allocationRepository = allocationRepository;
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> search(TransactionFilter filter, Pageable pageable) {
        Page<BankTransaction> page = transactionRepository.findAll(TransactionSpecifications.matching(filter), pageable);
        List<Long> ids = page.getContent().stream().map(BankTransaction::getId).toList();

        // two extra queries for the whole page instead of two per row
        Map<Long, MatchSummary> best = new HashMap<>();
        if (!ids.isEmpty()) {
            for (MatchSuggestion s : suggestionRepository.findPendingForTransactions(ids)) {
                best.putIfAbsent(s.getTransaction().getId(), new MatchSummary(s.getRule(), s.getRule().getLabel(),
                        s.getConfidence(), s.getScore(),
                        s.getItems().stream().map(item -> item.getInvoice().getNumber()).toList()));
            }
        }
        Map<Long, List<String>> matched = ids.isEmpty() ? Map.of() : allocationRepository.findByTransactionIdIn(ids).stream()
                .collect(Collectors.groupingBy(a -> a.getTransaction().getId(),
                        Collectors.mapping(a -> a.getInvoice().getNumber(), Collectors.toList())));

        return page.map(tx -> TransactionResponse.from(tx, best.get(tx.getId()), matched.getOrDefault(tx.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public TransactionDetailsResponse get(Long id) {
        BankTransaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Transaction", id));
        List<SuggestionResponse> suggestions = suggestionRepository.findByTransactionIdOrderByScoreDesc(id).stream()
                .map(SuggestionResponse::from).toList();
        List<Allocation> allocations = allocationRepository.findByTransactionIdOrderById(id);
        List<String> matchedInvoices = allocations.stream().map(a -> a.getInvoice().getNumber()).toList();
        return new TransactionDetailsResponse(
                TransactionResponse.from(transaction, null, matchedInvoices),
                transaction.getStatementImport().getFileName(),
                transaction.getRawData(),
                suggestions,
                allocations.stream().map(AllocationResponse::from).toList());
    }
}
