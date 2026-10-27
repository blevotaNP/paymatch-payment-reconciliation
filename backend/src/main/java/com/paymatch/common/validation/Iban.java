package com.paymatch.common.validation;

import java.math.BigInteger;
import java.util.Map;

/**
 * IBAN format and mod-97 check. A bare 26-digit Polish account number (NRB) is treated as a PL IBAN,
 * because that is how most Polish banks print accounts in statements.
 */
public final class Iban {

    private static final Map<String, Integer> KNOWN_LENGTHS = Map.of(
            "PL", 28, "DE", 22, "GB", 22, "CZ", 24, "SK", 24, "LT", 20, "FR", 27, "NL", 18);

    private Iban() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\s-]", "").toUpperCase();
        if (cleaned.matches("\\d{26}")) {
            cleaned = "PL" + cleaned;
        }
        return cleaned;
    }

    public static boolean isValid(String value) {
        String iban = normalize(value);
        if (iban == null || !iban.matches("[A-Z]{2}\\d{2}[A-Z0-9]{10,30}")) {
            return false;
        }
        Integer expectedLength = KNOWN_LENGTHS.get(iban.substring(0, 2));
        if (expectedLength != null && iban.length() != expectedLength) {
            return false;
        }
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.getNumericValue(c));
        }
        return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }
}
