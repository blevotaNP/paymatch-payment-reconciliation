package com.paymatch.common.parsing;

import org.apache.commons.csv.CSVFormat;

public final class CsvSupport {

    private CsvSupport() {
    }

    /** Polish exports mostly use ';' because ',' is the decimal separator. Picks whichever appears more in the header. */
    public static char detectDelimiter(String headerLine) {
        long semicolons = headerLine.chars().filter(c -> c == ';').count();
        long commas = headerLine.chars().filter(c -> c == ',').count();
        long tabs = headerLine.chars().filter(c -> c == '\t').count();
        if (tabs > semicolons && tabs > commas) {
            return '\t';
        }
        return semicolons >= commas ? ';' : ',';
    }

    public static CSVFormat format(char delimiter) {
        return CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();
    }

    public static String firstLine(String text) {
        int end = text.indexOf('\n');
        return end < 0 ? text : text.substring(0, end);
    }
}
