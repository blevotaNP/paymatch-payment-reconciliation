package com.paymatch.transaction;

import java.time.LocalDate;

public record TransactionFilter(String search, Direction direction, Long importId, LocalDate from, LocalDate to) {

    public enum Direction {
        INCOMING,
        OUTGOING
    }
}
