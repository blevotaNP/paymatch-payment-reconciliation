package com.paymatch.transaction;

import com.paymatch.statement.StatementImport;
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
import java.time.LocalDate;

@Entity
@Table(name = "bank_transaction")
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_id")
    private StatementImport statementImport;

    /** Our own account the statement belongs to, when the file tells us. */
    private String accountIban;

    @Column(nullable = false)
    private LocalDate bookingDate;

    private LocalDate valueDate;

    /** Signed: positive is money received, negative is money paid out. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    private String counterpartyName;
    private String counterpartyAccount;
    private String title;
    private String bankReference;

    @Column(nullable = false)
    private String dedupKey;

    private String rawData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus matchStatus = MatchStatus.UNMATCHED;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected BankTransaction() {
    }

    public BankTransaction(StatementImport statementImport, String accountIban, LocalDate bookingDate, LocalDate valueDate,
                           BigDecimal amount, String currency, String counterpartyName, String counterpartyAccount,
                           String title, String bankReference, String dedupKey, String rawData) {
        this.statementImport = statementImport;
        this.accountIban = accountIban;
        this.bookingDate = bookingDate;
        this.valueDate = valueDate;
        this.amount = amount;
        this.currency = currency;
        this.counterpartyName = counterpartyName;
        this.counterpartyAccount = counterpartyAccount;
        this.title = title;
        this.bankReference = bankReference;
        this.dedupKey = dedupKey;
        this.rawData = rawData;
    }

    public void setMatchStatus(MatchStatus matchStatus) {
        this.matchStatus = matchStatus;
    }

    public MatchStatus getMatchStatus() {
        return matchStatus;
    }

    public boolean isIncoming() {
        return amount.signum() > 0;
    }

    public Long getId() {
        return id;
    }

    public StatementImport getStatementImport() {
        return statementImport;
    }

    public String getAccountIban() {
        return accountIban;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCounterpartyName() {
        return counterpartyName;
    }

    public String getCounterpartyAccount() {
        return counterpartyAccount;
    }

    public String getTitle() {
        return title;
    }

    public String getBankReference() {
        return bankReference;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public String getRawData() {
        return rawData;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
