package com.paymatch.transaction;

import com.paymatch.statement.BankFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponse(
        Long id,
        Long importId,
        BankFormat bankFormat,
        String accountIban,
        LocalDate bookingDate,
        LocalDate valueDate,
        BigDecimal amount,
        String currency,
        String counterpartyName,
        String counterpartyAccount,
        String title,
        String bankReference) {

    public static TransactionResponse from(BankTransaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getStatementImport().getId(),
                t.getStatementImport().getBankFormat(),
                t.getAccountIban(),
                t.getBookingDate(),
                t.getValueDate(),
                t.getAmount(),
                t.getCurrency(),
                t.getCounterpartyName(),
                t.getCounterpartyAccount(),
                t.getTitle(),
                t.getBankReference());
    }
}
