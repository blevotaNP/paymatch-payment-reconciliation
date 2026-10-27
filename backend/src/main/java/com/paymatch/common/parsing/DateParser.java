package com.paymatch.common.parsing;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

public final class DateParser {

    private static final List<DateTimeFormatter> FORMATS = List.of(
            DateTimeFormatter.ofPattern("uuuu-MM-dd"),
            DateTimeFormatter.ofPattern("dd.MM.uuuu"),
            DateTimeFormatter.ofPattern("dd-MM-uuuu"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("uuuu.MM.dd"),
            DateTimeFormatter.ofPattern("uuuuMMdd"));

    private DateParser() {
    }

    public static LocalDate parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Date is empty");
        }
        String value = raw.trim();
        for (DateTimeFormatter format : FORMATS) {
            try {
                return LocalDate.parse(value, format.withResolverStyle(ResolverStyle.STRICT));
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        throw new IllegalArgumentException("Not a valid date: " + raw);
    }
}
