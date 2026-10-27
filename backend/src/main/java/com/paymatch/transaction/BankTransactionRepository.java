package com.paymatch.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long>, JpaSpecificationExecutor<BankTransaction> {

    @Override
    @EntityGraph(attributePaths = "statementImport")
    Page<BankTransaction> findAll(Specification<BankTransaction> spec, Pageable pageable);

    @Query("select t.dedupKey from BankTransaction t where t.dedupKey in :keys")
    Set<String> findExistingDedupKeys(@Param("keys") Collection<String> keys);

    List<BankTransaction> findByMatchStatusIn(Collection<MatchStatus> statuses);

    /**
     * SELECT ... FOR UPDATE. Decisions about the same payment wait for each other instead of interleaving
     * (interleaving deadlocked on the suggestion rows in tests); the one that waited then sees the new version.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from BankTransaction t where t.id = :id")
    Optional<BankTransaction> findForDecision(@Param("id") Long id);

    long countByMatchStatus(MatchStatus status);

    @Query("""
            select t from BankTransaction t join fetch t.statementImport
            where t.amount > 0
              and (cast(:from as localdate) is null or t.bookingDate >= :from)
              and (cast(:to as localdate) is null or t.bookingDate <= :to)
            order by t.bookingDate, t.id
            """)
    List<BankTransaction> findIncomingBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    long countByMatchStatusAndAmountGreaterThan(MatchStatus status, BigDecimal amount);

    boolean existsByStatementImportIdAndMatchStatus(Long importId, MatchStatus status);

    @Modifying
    @Query("delete from BankTransaction t where t.statementImport.id = :importId")
    int deleteByImportId(@Param("importId") Long importId);
}
