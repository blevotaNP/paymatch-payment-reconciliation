package com.paymatch.matching;

/** Summary of one matching run, shown after an import. */
public record MatchingRunResult(int checked, int autoConfirmed, int suggested, int unmatched) {

    public static MatchingRunResult empty() {
        return new MatchingRunResult(0, 0, 0, 0);
    }
}
