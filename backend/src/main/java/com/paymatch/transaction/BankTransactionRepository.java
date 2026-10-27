package com.paymatch.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long>, JpaSpecificationExecutor<BankTransaction> {

    @Override
    @EntityGraph(attributePaths = "statementImport")
    Page<BankTransaction> findAll(Specification<BankTransaction> spec, Pageable pageable);

    @Query("select t.dedupKey from BankTransaction t where t.dedupKey in :keys")
    Set<String> findExistingDedupKeys(@Param("keys") Collection<String> keys);

    @Modifying
    @Query("delete from BankTransaction t where t.statementImport.id = :importId")
    int deleteByImportId(@Param("importId") Long importId);
}
