package com.paymatch.allocation;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    @Query("select coalesce(sum(a.amount), 0) from Allocation a where a.invoice.id = :invoiceId")
    BigDecimal sumForInvoice(@Param("invoiceId") Long invoiceId);

    @EntityGraph(attributePaths = {"invoice", "invoice.counterparty", "createdBy"})
    List<Allocation> findByTransactionIdOrderById(Long transactionId);

    @EntityGraph(attributePaths = "invoice")
    List<Allocation> findByTransactionIdIn(Collection<Long> transactionIds);

    @EntityGraph(attributePaths = {"invoice", "invoice.counterparty", "createdBy", "transaction"})
    List<Allocation> findByInvoiceIdOrderByCreatedAt(Long invoiceId);

    @Query("select a from Allocation a join fetch a.invoice where a.transaction.statementImport.id = :importId")
    List<Allocation> findByImportId(@Param("importId") Long importId);

    @Query("select count(a) > 0 from Allocation a where a.transaction.statementImport.id = :importId and a.source = com.paymatch.allocation.AllocationSource.MANUAL")
    boolean existsManualForImport(@Param("importId") Long importId);

    @Modifying
    @Query("delete from Allocation a where a.transaction.id in (select t.id from BankTransaction t where t.statementImport.id = :importId)")
    int deleteByImportId(@Param("importId") Long importId);
}
