package com.paymatch.common.validation;

/**
 * Polish tax id (NIP): 10 digits, the last one is a weighted mod-11 checksum.
 */
public final class Nip {

    private static final int[] WEIGHTS = {6, 5, 7, 2, 3, 4, 5, 6, 7};

    private Nip() {
    }

    /** Strips separators and an optional "PL" prefix, e.g. "PL 526-000-12-46" -> "5260001246". */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\s-]", "").toUpperCase();
        if (cleaned.startsWith("PL")) {
            cleaned = cleaned.substring(2);
        }
        return cleaned;
    }

    public static boolean isValid(String value) {
        String nip = normalize(value);
        if (nip == null || !nip.matches("\\d{10}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += WEIGHTS[i] * (nip.charAt(i) - '0');
        }
        int checksum = sum % 11;
        return checksum != 10 && checksum == nip.charAt(9) - '0';
    }
}
