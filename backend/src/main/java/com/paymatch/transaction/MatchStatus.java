package com.paymatch.transaction;

public enum MatchStatus {
    /** No suggestion found (or an outgoing transaction, which is never matched). */
    UNMATCHED,
    /** Has pending suggestions waiting for review. */
    SUGGESTED,
    /** Fully allocated to one or more invoices. */
    MATCHED,
    /** Marked as not an invoice payment (refund, fee, ...). */
    IGNORED
}
