package com.paymatch.transaction;

import java.time.LocalDate;
import java.util.List;

public record TransactionFilter(String search, Direction direction, Long importId, LocalDate from, LocalDate to,
                                List<MatchStatus> matchStatus) {

    public enum Direction {
        INCOMING,
        OUTGOING
    }
}
