package com.paymatch.matching;

public enum Confidence {
    HIGH,
    MEDIUM,
    LOW;

    public static Confidence fromScore(int score) {
        if (score >= 85) {
            return HIGH;
        }
        return score >= 55 ? MEDIUM : LOW;
    }
}
