package com.paymatch.invoice;

import com.paymatch.common.Money;
import com.paymatch.counterparty.Counterparty;
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
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "invoice")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "counterparty_id")
    private Counterparty counterparty;

    @Column(nullable = false)
    private LocalDate issueDate;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal grossAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * Sum of confirmed payment allocations. There is deliberately no setter: the value only changes
     * through {@link #applyPaidAmount}, called when allocations are confirmed or undone.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount = Money.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    @Version
    private long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected Invoice() {
    }

    public Invoice(String number, Counterparty counterparty, LocalDate issueDate, LocalDate dueDate,
                   BigDecimal grossAmount, String currency) {
        this.number = number;
        this.counterparty = counterparty;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.grossAmount = Money.of(grossAmount);
        this.currency = currency;
    }

    public void updateDetails(String number, Counterparty counterparty, LocalDate issueDate, LocalDate dueDate,
                              BigDecimal grossAmount, String currency) {
        this.number = number;
        this.counterparty = counterparty;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.grossAmount = Money.of(grossAmount);
        this.currency = currency;
        this.status = InvoiceStatus.from(this.grossAmount, paidAmount);
    }

    public void applyPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = Money.of(paidAmount);
        this.status = InvoiceStatus.from(grossAmount, this.paidAmount);
    }

    public BigDecimal getRemainingAmount() {
        BigDecimal remaining = grossAmount.subtract(paidAmount);
        return remaining.signum() > 0 ? remaining : Money.ZERO;
    }

    public boolean isOverdue(LocalDate today) {
        return status.isOpen() && dueDate.isBefore(today);
    }

    public long getDaysOverdue(LocalDate today) {
        return isOverdue(today) ? ChronoUnit.DAYS.between(dueDate, today) : 0;
    }

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public Counterparty getCounterparty() {
        return counterparty;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
