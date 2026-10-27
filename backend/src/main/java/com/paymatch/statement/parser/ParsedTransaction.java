package com.paymatch.statement.parser;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One transaction in a bank-neutral shape. Amount is signed: positive means money came in.
 */
public record ParsedTransaction(
        long lineNumber,
        LocalDate bookingDate,
        LocalDate valueDate,
        BigDecimal amount,
        String currency,
        String counterpartyName,
        String counterpartyAccount,
        String title,
        String bankReference,
        String rawData) {
}
