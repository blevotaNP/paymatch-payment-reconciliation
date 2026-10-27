package com.paymatch.statement;

import com.paymatch.statement.parser.ParsedTransaction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the key that makes a bank transaction unique across imports.
 * <ul>
 *   <li>With a bank reference: hash of format + own account + reference. References are unique per bank.</li>
 *   <li>Without one: fingerprint of own account + booking date + amount + counterparty account + title,
 *       plus the occurrence number of that fingerprint within the file. The occurrence number keeps two
 *       genuinely identical transfers on the same day (e.g. two equal bank fees) as two transactions,
 *       while the same pair in an overlapping export is still recognised as a duplicate.</li>
 * </ul>
 */
public final class DedupKeyGenerator {

    private DedupKeyGenerator() {
    }

    public static List<String> generate(BankFormat format, String accountIban, List<ParsedTransaction> transactions) {
        Map<String, Integer> occurrences = new HashMap<>();
        List<String> keys = new ArrayList<>(transactions.size());
        for (ParsedTransaction tx : transactions) {
            if (tx.bankReference() != null) {
                keys.add(sha256("REF|" + format + "|" + nullToEmpty(accountIban) + "|" + tx.bankReference()));
            } else {
                String fingerprint = "FP|" + nullToEmpty(accountIban)
                        + "|" + tx.bookingDate()
                        + "|" + tx.amount().toPlainString()
                        + "|" + normalizeAccount(tx.counterpartyAccount())
                        + "|" + normalizeText(tx.title());
                int occurrence = occurrences.merge(fingerprint, 1, Integer::sum);
                keys.add(sha256(fingerprint + "|" + occurrence));
            }
        }
        return keys;
    }

    /** Uppercase, no diacritics, letters and digits only: exports of the same bank are not always consistent. */
    static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        String withoutDiacritics = Normalizer.normalize(value.replace('ł', 'l').replace('Ł', 'L'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutDiacritics.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String normalizeAccount(String account) {
        return account == null ? "" : account.replaceAll("[^0-9]", "");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
