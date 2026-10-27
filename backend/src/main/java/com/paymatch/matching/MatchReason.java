package com.paymatch.matching;

/**
 * One piece of evidence for a suggestion, e.g. field "title", "Invoice number FV/1/09/2026 found as 'FV 1 09 2026'".
 * Field is one of: title, amount, account, nip, name, invoice.
 */
public record MatchReason(String field, String message) {
}
