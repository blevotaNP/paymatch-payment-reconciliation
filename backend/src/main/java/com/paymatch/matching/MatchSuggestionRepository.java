package com.paymatch.matching;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MatchSuggestionRepository extends JpaRepository<MatchSuggestion, Long> {

    @EntityGraph(attributePaths = {"items", "items.invoice", "items.invoice.counterparty"})
    List<MatchSuggestion> findByTransactionIdOrderByScoreDesc(Long transactionId);

    @Modifying
    @Query("delete from MatchSuggestion s where s.transaction.id in :transactionIds and s.status = com.paymatch.matching.SuggestionStatus.PENDING")
    int deletePendingForTransactions(@Param("transactionIds") Collection<Long> transactionIds);

    @Query("select distinct i.suggestion.transaction.id from MatchSuggestionItem i where i.invoice.id = :invoiceId and i.suggestion.status = com.paymatch.matching.SuggestionStatus.PENDING")
    List<Long> findTransactionIdsWithPendingSuggestionFor(@Param("invoiceId") Long invoiceId);

    @Query("select s from MatchSuggestion s join fetch s.items i join fetch i.invoice where s.transaction.id in :transactionIds and s.status = com.paymatch.matching.SuggestionStatus.REJECTED")
    List<MatchSuggestion> findRejectedForTransactions(@Param("transactionIds") Collection<Long> transactionIds);

    /** Best pending suggestion per transaction, for list views. */
    @Query("""
            select distinct s from MatchSuggestion s join fetch s.items i join fetch i.invoice
            where s.transaction.id in :transactionIds and s.status = com.paymatch.matching.SuggestionStatus.PENDING
            order by s.score desc
            """)
    List<MatchSuggestion> findPendingForTransactions(@Param("transactionIds") Collection<Long> transactionIds);
}
