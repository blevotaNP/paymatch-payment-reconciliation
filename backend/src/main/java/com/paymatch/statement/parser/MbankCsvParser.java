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

/**
 * mBank "Lista operacji" CSV. Starts with a metadata block (account number, period), header columns are
 * prefixed with '#', account numbers are wrapped in single quotes, and the file ends with summary rows
 * such as "#Saldo końcowe". Name and address of the counterparty share one column.
 */
@Component
public class MbankCsvParser implements StatementParser {

    private static final String DATE = "Data operacji";
    private static final String BOOKING_DATE = "Data księgowania";
    private static final String OPERATION = "Opis operacji";
    private static final String TITLE = "Tytuł";
    private static final String COUNTERPARTY = "Nadawca/Odbiorca";
    private static final String ACCOUNT = "Numer konta";
    private static final String AMOUNT = "Kwota";

    @Override
    public BankFormat format() {
        return BankFormat.MBANK_CSV;
    }

    @Override
    public boolean supports(String content) {
        return CsvStatementSupport.findHeader(CsvStatementSupport.lines(content), MbankCsvParser::isHeader) >= 0;
    }

    private static boolean isHeader(String line) {
        return line.contains("#" + DATE) && line.contains("#" + COUNTERPARTY);
    }

    @Override
    public ParsedStatement parse(String content) {
        String[] lines = CsvStatementSupport.lines(content);
        int headerIndex = CsvStatementSupport.findHeader(lines, MbankCsvParser::isHeader);
        if (headerIndex < 0) {
            throw new StatementParseException("mBank header row not found (expected \"#Data operacji\" and \"#Nadawca/Odbiorca\")");
        }

        ParsedStatement statement = new ParsedStatement();
        statement.setAccountIban(CsvStatementSupport.findAccount(lines, headerIndex));
        LocalDate[] period = CsvStatementSupport.findPeriod(lines, headerIndex);
        if (period != null) {
            statement.includePeriod(period[0], period[1]);
        }

        Table table = CsvStatementSupport.readTable(lines, headerIndex);
        Columns columns = table.columns();
        columns.require(DATE, AMOUNT, TITLE);

        for (Row row : table.rows()) {
            CSVRecord record = row.record();
            if (CsvStatementSupport.isNonTransactionRow(record)) {
                continue;
            }
            try {
                LocalDate operationDate = DateParser.parse(columns.get(record, DATE));
                String booking = columns.get(record, BOOKING_DATE);
                BigDecimal amount = AmountParser.parse(columns.get(record, AMOUNT));
                if (amount.signum() == 0) {
                    statement.skip(row.line(), "Zero amount", row.raw());
                    continue;
                }
                String title = columns.get(record, TITLE);
                statement.addTransaction(new ParsedTransaction(
                        row.line(),
                        booking != null ? DateParser.parse(booking) : operationDate,
                        operationDate,
                        amount,
                        "PLN",
                        columns.get(record, COUNTERPARTY),
                        CsvStatementSupport.cleanAccount(columns.get(record, ACCOUNT)),
                        title != null ? title : columns.get(record, OPERATION),
                        null,
                        row.raw()));
            } catch (IllegalArgumentException e) {
                statement.skip(row.line(), e.getMessage(), row.raw());
            }
        }
        return statement;
    }
}
