package com.paymatch.audit;

import com.paymatch.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Written in the caller's transaction on purpose: if the change rolls back, its audit entry does too,
 * so the log never claims something that did not happen.
 */
@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final CurrentUser currentUser;

    public AuditService(AuditEventRepository repository, CurrentUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, AuditEntityType entityType, Long entityId, String summary,
                       List<Long> invoiceIds, Map<String, Object> details) {
        repository.save(new AuditEvent(currentUser.find().orElse(null), action, entityType, entityId, summary, invoiceIds, details));
    }

    /** For actions the system takes on its own, e.g. automatic matching. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSystem(AuditAction action, AuditEntityType entityType, Long entityId, String summary,
                             List<Long> invoiceIds, Map<String, Object> details) {
        repository.save(new AuditEvent(null, action, entityType, entityId, summary, invoiceIds, details));
    }
}
