package com.paymatch.allocation;

import com.paymatch.invoice.Invoice;
import com.paymatch.matching.MatchSuggestion;
import com.paymatch.transaction.BankTransaction;
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

import java.math.BigDecimal;
import java.time.Instant;

/** A confirmed part of a bank transaction that pays a given invoice. */
@Entity
@Table(name = "allocation")
public class Allocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id")
    private BankTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AllocationSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggestion_id")
    private MatchSuggestion suggestion;

    /** Null for automatic allocations. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Allocation() {
    }

    public Allocation(BankTransaction transaction, Invoice invoice, BigDecimal amount, AllocationSource source,
                      MatchSuggestion suggestion, AppUser createdBy) {
        this.transaction = transaction;
        this.invoice = invoice;
        this.amount = amount;
        this.source = source;
        this.suggestion = suggestion;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public BankTransaction getTransaction() {
        return transaction;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public AllocationSource getSource() {
        return source;
    }

    public MatchSuggestion getSuggestion() {
        return suggestion;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
