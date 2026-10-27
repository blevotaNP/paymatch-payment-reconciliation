package com.paymatch.statement.parser;

import com.paymatch.common.parsing.AmountParser;
import com.paymatch.common.parsing.DateParser;
import com.paymatch.statement.BankFormat;
import com.paymatch.statement.parser.CsvStatementSupport.Columns;
import com.paymatch.statement.parser.CsvStatementSupport.Row;
import com.paymatch.statement.parser.CsvStatementSupport.Table;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

/**
 * ING "Lista transakcji" CSV. Has a unique "Nr transakcji" per row, which we use as the bank reference.
 * Card authorizations that are not booked yet appear with an empty amount and a value in
 * "Kwota blokady"; they are skipped because they may still change or disappear.
 */
@Component
public class IngCsvParser implements StatementParser {

    private static final String DATE = "Data transakcji";
    private static final String BOOKING_DATE = "Data księgowania";
    private static final String COUNTERPARTY = "Dane kontrahenta";
    private static final String TITLE = "Tytuł";
    private static final String ACCOUNT = "Nr rachunku";
    private static final String DETAILS = "Szczegóły";
    private static final String REFERENCE = "Nr transakcji";
    private static final String AMOUNT = "Kwota transakcji (waluta rachunku)";
    private static final String CURRENCY = "Waluta";
    private static final String BLOCKED_AMOUNT = "Kwota blokady/zwolnienie blokady";

    @Override
    public BankFormat format() {
        return BankFormat.ING_CSV;
    }

    @Override
    public boolean supports(String content) {
        return CsvStatementSupport.findHeader(CsvStatementSupport.lines(content), IngCsvParser::isHeader) >= 0;
    }

    private static boolean isHeader(String line) {
        return line.contains(DATE) && line.contains(COUNTERPARTY) && line.contains(REFERENCE);
    }

    @Override
    public ParsedStatement parse(String content) {
        String[] lines = CsvStatementSupport.lines(content);
        int headerIndex = CsvStatementSupport.findHeader(lines, IngCsvParser::isHeader);
        if (headerIndex < 0) {
            throw new StatementParseException("ING header row not found (expected \"Data transakcji\", \"Dane kontrahenta\", \"Nr transakcji\")");
        }

        ParsedStatement statement = new ParsedStatement();
        statement.setAccountIban(CsvStatementSupport.findAccount(lines, headerIndex));
        LocalDate[] period = CsvStatementSupport.findPeriod(lines, headerIndex);
        if (period != null) {
            statement.includePeriod(period[0], period[1]);
        }

        Table table = CsvStatementSupport.readTable(lines, headerIndex);
        Columns columns = table.columns();
        columns.require(DATE, AMOUNT, TITLE, REFERENCE);

        for (Row row : table.rows()) {
            CSVRecord record = row.record();
            if (CsvStatementSupport.isNonTransactionRow(record)) {
                continue;
            }
            try {
                String rawAmount = columns.get(record, AMOUNT);
                if (rawAmount == null && columns.get(record, BLOCKED_AMOUNT) != null) {
                    statement.skip(row.line(), "Card authorization not booked yet (blocked amount)", row.raw());
                    continue;
                }
                LocalDate transactionDate = DateParser.parse(columns.get(record, DATE));
                String booking = columns.get(record, BOOKING_DATE);
                BigDecimal amount = AmountParser.parse(rawAmount);
                if (amount.signum() == 0) {
                    statement.skip(row.line(), "Zero amount", row.raw());
                    continue;
                }
                String title = columns.get(record, TITLE);
                String currency = columns.get(record, CURRENCY);
                statement.addTransaction(new ParsedTransaction(
                        row.line(),
                        booking != null ? DateParser.parse(booking) : transactionDate,
                        transactionDate,
                        amount,
                        currency != null ? currency.toUpperCase(Locale.ROOT) : "PLN",
                        columns.get(record, COUNTERPARTY),
                        CsvStatementSupport.cleanAccount(columns.get(record, ACCOUNT)),
                        title != null ? title : columns.get(record, DETAILS),
                        columns.get(record, REFERENCE),
                        row.raw()));
            } catch (IllegalArgumentException e) {
                statement.skip(row.line(), e.getMessage(), row.raw());
            }
        }
        return statement;
    }
}
