package com.paymatch.matching;

import com.paymatch.matching.InvoiceNumberTokens.Token;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The data the rules need about an invoice. Paid amount is mutable so that one matching run sees
 * the effect of allocations it has already made (two transfers for the same invoice in one file).
 */
public class InvoiceSnapshot {

    private final Long id;
    private final String number;
    private final Long counterpartyId;
    private final String counterpartyName;
    private final String currency;
    private final BigDecimal grossAmount;
    private final LocalDate dueDate;
    private final List<Token> tokens;
    private final int prefixLength;
    private BigDecimal paidAmount;

    public InvoiceSnapshot(Long id, String number, Long counterpartyId, String counterpartyName, String currency,
                           BigDecimal grossAmount, BigDecimal paidAmount, LocalDate dueDate) {
        this.id = id;
        this.number = number;
        this.counterpartyId = counterpartyId;
        this.counterpartyName = counterpartyName;
        this.currency = currency;
        this.grossAmount = grossAmount;
        this.paidAmount = paidAmount;
        this.dueDate = dueDate;
        this.tokens = InvoiceNumberTokens.tokenize(number);
        int prefix = 0;
        while (prefix < tokens.size() && !tokens.get(prefix).numeric()) {
            prefix++;
        }
        this.prefixLength = prefix;
    }

    public BigDecimal remainingAmount() {
        BigDecimal remaining = grossAmount.subtract(paidAmount);
        return remaining.signum() > 0 ? remaining : BigDecimal.ZERO.setScale(2);
    }

    public boolean isOpen() {
        return paidAmount.compareTo(grossAmount) < 0;
    }

    public void addPaid(BigDecimal amount) {
        paidAmount = paidAmount.add(amount);
    }

    /** Letter prefix of the number, e.g. "FV" for "FV/10/2026"; empty when the number starts with a digit. */
    public String prefix() {
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < prefixLength; i++) {
            prefix.append(tokens.get(i).value());
        }
        return prefix.toString();
    }

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public Long getCounterpartyId() {
        return counterpartyId;
    }

    public String getCounterpartyName() {
        return counterpartyName;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    List<Token> getTokens() {
        return tokens;
    }

    int getPrefixLength() {
        return prefixLength;
    }
}
