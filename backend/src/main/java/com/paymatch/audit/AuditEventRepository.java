package com.paymatch.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    @EntityGraph(attributePaths = "user")
    Page<AuditEvent> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<AuditEvent> findByActionInOrderByOccurredAtDescIdDesc(Collection<AuditAction> actions, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    List<AuditEvent> findByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc(AuditEntityType entityType, Long entityId);

    @Query(value = "select * from audit_event where :invoiceId = any(invoice_ids) or (entity_type = 'INVOICE' and entity_id = :invoiceId) "
            + "order by occurred_at desc, id desc limit 100", nativeQuery = true)
    List<AuditEvent> findForInvoice(@Param("invoiceId") Long invoiceId);
}
