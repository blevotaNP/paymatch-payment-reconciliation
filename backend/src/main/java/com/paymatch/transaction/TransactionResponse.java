package com.paymatch.transaction;

import com.paymatch.matching.Confidence;
import com.paymatch.matching.MatchRule;
import com.paymatch.statement.BankFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
        String bankReference,
        MatchStatus matchStatus,
        /** Best pending suggestion, when the status is SUGGESTED. */
        MatchSummary bestSuggestion,
        /** Invoice numbers the transaction is allocated to, when MATCHED. */
        List<String> matchedInvoices) {

    public static TransactionResponse from(BankTransaction t, MatchSummary bestSuggestion, List<String> matchedInvoices) {
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
                t.getBankReference(),
                t.getMatchStatus(),
                bestSuggestion,
                matchedInvoices);
    }

    public record MatchSummary(MatchRule rule, String ruleLabel, Confidence confidence, int score, List<String> invoiceNumbers) {
    }
}
