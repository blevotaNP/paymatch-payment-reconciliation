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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PKO BP "Historia rachunku" CSV. The interesting data is not in fixed columns: "Opis transakcji" and
 * the unnamed columns after it hold "Key: value" pairs such as "Tytuł: FV/1/2026" or
 * "Rachunek nadawcy: 12 1020 ...". The file contains no own account number and no bank reference.
 */
@Component
public class PkoBpCsvParser implements StatementParser {

    private static final String DATE = "Data operacji";
    private static final String VALUE_DATE = "Data waluty";
    private static final String TYPE = "Typ transakcji";
    private static final String AMOUNT = "Kwota";
    private static final String CURRENCY = "Waluta";
    private static final String DESCRIPTION = "Opis transakcji";
    // card payments carry the merchant only in "Lokalizacja: Adres: ORLEN STACJA 123 Miasto: WARSZAWA Kraj: POLSKA"
    private static final Pattern CARD_MERCHANT = Pattern.compile("Adres:\\s*(.+?)\\s*(?:Miasto:|Kraj:|$)");

    @Override
    public BankFormat format() {
        return BankFormat.PKO_BP_CSV;
    }

    @Override
    public boolean supports(String content) {
        return CsvStatementSupport.findHeader(CsvStatementSupport.lines(content), PkoBpCsvParser::isHeader) >= 0;
    }

    private static boolean isHeader(String line) {
        return line.contains(DATE) && line.contains(TYPE) && line.contains(DESCRIPTION);
    }

    @Override
    public ParsedStatement parse(String content) {
        String[] lines = CsvStatementSupport.lines(content);
        int headerIndex = CsvStatementSupport.findHeader(lines, PkoBpCsvParser::isHeader);
        if (headerIndex < 0) {
            throw new StatementParseException("PKO BP header row not found (expected \"Data operacji\", \"Typ transakcji\", \"Opis transakcji\")");
        }
        Table table = CsvStatementSupport.readTable(lines, headerIndex);
        Columns columns = table.columns();
        columns.require(DATE, AMOUNT, DESCRIPTION);
        int descriptionIndex = columns.index(DESCRIPTION);

        ParsedStatement statement = new ParsedStatement();
        for (Row row : table.rows()) {
            CSVRecord record = row.record();
            if (CsvStatementSupport.isNonTransactionRow(record)) {
                continue;
            }
            try {
                LocalDate bookingDate = DateParser.parse(columns.get(record, DATE));
                String valueDate = columns.get(record, VALUE_DATE);
                BigDecimal amount = AmountParser.parse(columns.get(record, AMOUNT));
                if (amount.signum() == 0) {
                    statement.skip(row.line(), "Zero amount", row.raw());
                    continue;
                }
                Description description = readDescription(record, descriptionIndex);
                String currency = columns.get(record, CURRENCY);
                String title = description.title != null ? description.title : columns.get(record, TYPE);

                statement.addTransaction(new ParsedTransaction(
                        row.line(),
                        bookingDate,
                        valueDate != null ? DateParser.parse(valueDate) : bookingDate,
                        amount,
                        currency != null ? currency.toUpperCase(Locale.ROOT) : "PLN",
                        description.name,
                        CsvStatementSupport.cleanAccount(description.account),
                        title,
                        null,
                        row.raw()));
                statement.includePeriod(bookingDate, bookingDate);
            } catch (IllegalArgumentException e) {
                statement.skip(row.line(), e.getMessage(), row.raw());
            }
        }
        return statement;
    }

    private Description readDescription(CSVRecord record, int fromIndex) {
        Description description = new Description();
        List<String> freeText = new ArrayList<>();
        for (int i = fromIndex; i < record.size(); i++) {
            String value = CsvStatementSupport.clean(record.get(i));
            if (value == null) {
                continue;
            }
            int colon = value.indexOf(':');
            String key = colon > 0 ? value.substring(0, colon).trim().toLowerCase(Locale.ROOT) : "";
            String text = colon > 0 ? CsvStatementSupport.clean(value.substring(colon + 1)) : value;
            switch (key) {
                case "tytuł", "tytul" -> description.title = text;
                case "nazwa nadawcy", "nazwa odbiorcy" -> description.name = text;
                case "rachunek nadawcy", "rachunek odbiorcy" -> description.account = text;
                case "lokalizacja" -> {
                    Matcher merchant = CARD_MERCHANT.matcher(text == null ? "" : text);
                    if (description.name == null && merchant.find()) {
                        description.name = merchant.group(1);
                    }
                }
                case "adres nadawcy", "adres odbiorcy", "numer telefonu",
                     "referencje własne zleceniodawcy", "oryginalna kwota operacji" -> {
                    // not needed for matching
                }
                default -> freeText.add(value);
            }
        }
        if (description.title == null && !freeText.isEmpty()) {
            description.title = String.join(" ", freeText);
        }
        return description;
    }

    private static final class Description {
        String title;
        String name;
        String account;
    }
}
