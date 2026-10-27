package com.paymatch.common;

import java.text.Normalizer;
import java.util.Locale;

public final class TextNormalizer {

    private TextNormalizer() {
    }

    /** "Zakład Łączności" -> "ZAKLAD LACZNOSCI". Ł has no decomposed form, so it is replaced by hand. */
    public static String upperAscii(String value) {
        if (value == null) {
            return "";
        }
        String withoutDiacritics = Normalizer.normalize(value.replace('ł', 'l').replace('Ł', 'L'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutDiacritics.toUpperCase(Locale.ROOT);
    }

    /** Letters and digits only, uppercase, no diacritics. */
    public static String alphanumeric(String value) {
        return upperAscii(value).replaceAll("[^A-Z0-9]", "");
    }
}
