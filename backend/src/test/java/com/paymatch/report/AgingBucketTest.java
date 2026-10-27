package com.paymatch.report;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AgingBucketTest {

    @ParameterizedTest
    @CsvSource({
            "-10, NOT_DUE",
            "0, NOT_DUE",
            "1, DAYS_1_30",
            "30, DAYS_1_30",
            "31, DAYS_31_60",
            "60, DAYS_31_60",
            "61, DAYS_61_90",
            "90, DAYS_61_90",
            "91, DAYS_90_PLUS",
            "400, DAYS_90_PLUS"
    })
    void putsDaysOverdueIntoTheRightBucket(long days, AgingBucket expected) {
        assertThat(AgingBucket.forDaysOverdue(days)).isEqualTo(expected);
    }
}
