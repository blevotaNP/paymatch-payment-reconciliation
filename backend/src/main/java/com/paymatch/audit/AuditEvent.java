package com.paymatch.audit;

import com.paymatch.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Append-only record of who did what. Never updated or deleted by the application. */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    /** Null when the system acted on its own (automatic matching). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditEntityType entityType;

    private Long entityId;

    @Column(nullable = false)
    private String summary;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private Long[] invoiceIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> details = new LinkedHashMap<>();

    protected AuditEvent() {
    }

    public AuditEvent(AppUser user, AuditAction action, AuditEntityType entityType, Long entityId, String summary,
                      List<Long> invoiceIds, Map<String, Object> details) {
        this.user = user;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.summary = summary.length() > 500 ? summary.substring(0, 500) : summary;
        this.invoiceIds = invoiceIds.toArray(Long[]::new);
        this.details = new LinkedHashMap<>(details);
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public AppUser getUser() {
        return user;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditEntityType getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getSummary() {
        return summary;
    }

    public Long[] getInvoiceIds() {
        return invoiceIds;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
