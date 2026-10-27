package com.paymatch.common.parsing;

import com.paymatch.common.Money;

import java.math.BigDecimal;

/**
 * Parses amounts as they appear in Polish bank exports and spreadsheets:
 * "1 230,50", "1230.50", "1.230,50", "-45,00", "+1 000,00 PLN".
 * A single comma or a single dot is treated as the decimal separator.
 */
public final class AmountParser {

    private AmountParser() {
    }

    public static BigDecimal parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Amount is empty");
        }
        String value = raw.trim()
                .replaceAll("(?i)(pln|zł|zl|eur|usd)$", "")
                // regular, non-breaking and narrow non-breaking spaces used as thousand separators
                .replaceAll("[\\s\\u00A0\\u202F']", "");

        int lastComma = value.lastIndexOf(',');
        int lastDot = value.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            if (lastComma > lastDot) {
                value = value.replace(".", "").replace(',', '.');
            } else {
                value = value.replace(",", "");
            }
        } else if (lastComma >= 0) {
            value = value.replace(',', '.');
        } else if (value.indexOf('.') != lastDot) {
            // more than one dot, so dots are thousand separators: "1.230.500"
            value = value.replace(".", "");
        }

        if (value.startsWith("+")) {
            value = value.substring(1);
        }
        if (!value.matches("-?\\d+(\\.\\d+)?")) {
            throw new IllegalArgumentException("Not a valid amount: " + raw);
        }
        return Money.of(new BigDecimal(value));
    }
}
