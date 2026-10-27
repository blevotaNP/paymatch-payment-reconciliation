package com.paymatch.invoice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    @Override
    @EntityGraph(attributePaths = "counterparty")
    Page<Invoice> findAll(Specification<Invoice> spec, Pageable pageable);

    boolean existsByNumberIgnoreCase(String number);

    boolean existsByNumberIgnoreCaseAndIdNot(String number, Long id);

    boolean existsByCounterpartyId(Long counterpartyId);

    @Query("select i from Invoice i join fetch i.counterparty")
    List<Invoice> findAllWithCounterparty();

    @Query("select i from Invoice i join fetch i.counterparty")
    List<Invoice> findAllWithCounterparty(Sort sort);

    @Query("select i from Invoice i join fetch i.counterparty where i.status in :statuses and i.currency = :currency order by i.dueDate")
    List<Invoice> findOpenWithCounterparty(@Param("statuses") Collection<InvoiceStatus> statuses, @Param("currency") String currency);
}
