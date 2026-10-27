package com.paymatch.statement.parser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Result of parsing one file. Parsers fill it while reading; rows that cannot be read go to {@code skipped}. */
public class ParsedStatement {

    private String accountIban;
    private LocalDate periodFrom;
    private LocalDate periodTo;
    private final List<ParsedTransaction> transactions = new ArrayList<>();
    private final List<SkippedRow> skipped = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public void addTransaction(ParsedTransaction transaction) {
        transactions.add(transaction);
    }

    public void skip(long line, String reason, String content) {
        skipped.add(new SkippedRow(line, reason, content));
    }

    public void warn(String warning) {
        warnings.add(warning);
    }

    /** Widens the statement period; called for each statement block or header found in the file. */
    public void includePeriod(LocalDate from, LocalDate to) {
        if (from != null && (periodFrom == null || from.isBefore(periodFrom))) {
            periodFrom = from;
        }
        if (to != null && (periodTo == null || to.isAfter(periodTo))) {
            periodTo = to;
        }
    }

    public String getAccountIban() {
        return accountIban;
    }

    public void setAccountIban(String accountIban) {
        this.accountIban = accountIban;
    }

    public LocalDate getPeriodFrom() {
        return periodFrom;
    }

    public LocalDate getPeriodTo() {
        return periodTo;
    }

    public List<ParsedTransaction> getTransactions() {
        return transactions;
    }

    public List<SkippedRow> getSkipped() {
        return skipped;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public int getTotalRows() {
        return transactions.size() + skipped.size();
    }
}
