package com.paymatch.audit;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public record AuditEventResponse(Long id, Instant occurredAt, String user, AuditAction action, AuditEntityType entityType,
                                 Long entityId, String summary, List<Long> invoiceIds, Map<String, Object> details) {

    public static AuditEventResponse from(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getOccurredAt(),
                e.getUser() != null ? e.getUser().getDisplayName() : null,
                e.getAction(), e.getEntityType(), e.getEntityId(), e.getSummary(),
                Arrays.asList(e.getInvoiceIds()), e.getDetails());
    }
}
