package com.paymatch.report;

/** Standard receivables aging buckets, by days past the due date. */
public enum AgingBucket {
    NOT_DUE("Not yet due"),
    DAYS_1_30("1–30 days"),
    DAYS_31_60("31–60 days"),
    DAYS_61_90("61–90 days"),
    DAYS_90_PLUS("Over 90 days");

    private final String label;

    AgingBucket(String label) {
        this.label = label;
    }

    public static AgingBucket forDaysOverdue(long days) {
        if (days <= 0) {
            return NOT_DUE;
        }
        if (days <= 30) {
            return DAYS_1_30;
        }
        if (days <= 60) {
            return DAYS_31_60;
        }
        return days <= 90 ? DAYS_61_90 : DAYS_90_PLUS;
    }

    public String getLabel() {
        return label;
    }
}
