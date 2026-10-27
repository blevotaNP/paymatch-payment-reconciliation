package com.paymatch.statement.parser;

import com.paymatch.common.parsing.CsvSupport;
import com.paymatch.common.parsing.DateParser;
import com.paymatch.common.validation.Iban;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared helpers for bank CSV exports. They usually start with a few lines of metadata (account, period),
 * then a header row, then transactions, then summary lines.
 */
final class CsvStatementSupport {

    private static final Pattern ACCOUNT = Pattern.compile("(?:PL\\s?)?\\d{2}(?:\\s?\\d{4}){6}");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}|\\d{2}\\.\\d{2}\\.\\d{4}");

    private CsvStatementSupport() {
    }

    static String[] lines(String content) {
        return content.split("\\r?\\n", -1);
    }

    static int findHeader(String[] lines, Predicate<String> isHeader) {
        for (int i = 0; i < lines.length; i++) {
            if (isHeader.test(lines[i])) {
                return i;
            }
        }
        return -1;
    }

    /** Reads everything from the header line on. Line numbers are 1-based positions in the original file. */
    static Table readTable(String[] lines, int headerIndex) {
        String text = String.join("\n", Arrays.copyOfRange(lines, headerIndex, lines.length));
        char delimiter = CsvSupport.detectDelimiter(lines[headerIndex]);
        try (CSVParser parser = CsvSupport.format(delimiter).builder()
                .setIgnoreSurroundingSpaces(true)
                .get()
                .parse(new StringReader(text))) {
            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) {
                throw new StatementParseException("Header row is missing");
            }
            Columns columns = Columns.of(records.getFirst());
            List<Row> rows = new ArrayList<>();
            // turn each record's character offset back into a line number of the original file
            int lineIndex = headerIndex;
            long lineEnd = lines[headerIndex].length() + 1L;
            for (CSVRecord record : records.subList(1, records.size())) {
                while (record.getCharacterPosition() >= lineEnd && lineIndex < lines.length - 1) {
                    lineIndex++;
                    lineEnd += lines[lineIndex].length() + 1L;
                }
                rows.add(new Row(lineIndex + 1, record));
            }
            return new Table(columns, rows);
        } catch (IOException | UncheckedIOException | IllegalStateException e) {
            throw new StatementParseException("Could not read CSV: " + e.getMessage());
        }
    }

    /** Summary rows, footers and blank rows carry no transaction and are ignored without being reported. */
    static boolean isNonTransactionRow(CSVRecord record) {
        String first = record.size() > 0 ? record.get(0).trim() : "";
        long filled = record.stream().filter(value -> !value.isBlank()).count();
        return first.isEmpty() || first.startsWith("#") || filled < 3;
    }

    static String findAccount(String[] lines, int untilIndex) {
        for (int i = 0; i < untilIndex; i++) {
            Matcher m = ACCOUNT.matcher(lines[i]);
            if (m.find()) {
                String iban = Iban.normalize(m.group());
                if (Iban.isValid(iban)) {
                    return iban;
                }
            }
        }
        return null;
    }

    /** First line before the header with two dates is treated as the statement period. */
    static LocalDate[] findPeriod(String[] lines, int untilIndex) {
        for (int i = 0; i < untilIndex; i++) {
            Matcher m = DATE.matcher(lines[i]);
            List<String> dates = new ArrayList<>();
            while (m.find()) {
                dates.add(m.group());
            }
            if (dates.size() >= 2) {
                try {
                    return new LocalDate[]{DateParser.parse(dates.get(0)), DateParser.parse(dates.get(1))};
                } catch (IllegalArgumentException ignored) {
                    // not a period line
                }
            }
        }
        return null;
    }

    /** Collapses whitespace and strips the quotes some banks put around account numbers ('6110...'). */
    static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace(' ', ' ').replaceAll("^'+|'+$", "").replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    static String cleanAccount(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : Iban.normalize(cleaned);
    }

    record Row(long line, CSVRecord record) {

        String raw() {
            return String.join(";", record.toList());
        }
    }

    record Table(Columns columns, List<Row> rows) {
    }

    /** Column lookup by header name, ignoring case, quotes and the '#' prefix used by mBank. */
    static final class Columns {

        private final Map<String, Integer> indexes;

        private Columns(Map<String, Integer> indexes) {
            this.indexes = indexes;
        }

        static Columns of(CSVRecord header) {
            Map<String, Integer> indexes = new HashMap<>();
            for (int i = 0; i < header.size(); i++) {
                // ING repeats "Waluta" several times; the first one belongs to the transaction amount
                indexes.putIfAbsent(normalize(header.get(i)), i);
            }
            return new Columns(indexes);
        }

        boolean has(String name) {
            return indexes.containsKey(normalize(name));
        }

        int index(String name) {
            Integer index = indexes.get(normalize(name));
            if (index == null) {
                throw new StatementParseException("Missing column: " + name);
            }
            return index;
        }

        String get(CSVRecord record, String name) {
            Integer index = indexes.get(normalize(name));
            if (index == null || index >= record.size()) {
                return null;
            }
            return clean(record.get(index));
        }

        void require(String... names) {
            List<String> missing = Arrays.stream(names).filter(name -> !has(name)).toList();
            if (!missing.isEmpty()) {
                throw new StatementParseException("Missing columns: " + String.join(", ", missing));
            }
        }

        private static String normalize(String name) {
            return name.replace("#", "").replace("\"", "").trim().toLowerCase(Locale.ROOT);
        }
    }
}
