package com.paymatch.common.parsing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateParserTest {

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-05", "05.10.2026", "05-10-2026", "05/10/2026", "2026.10.05", "20261005"})
    void parsesSupportedFormats(String raw) {
        assertThat(DateParser.parse(raw)).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "31.04.2026", "10/2026", "yesterday", ""})
    void rejectsInvalidDates(String raw) {
        assertThatThrownBy(() -> DateParser.parse(raw)).isInstanceOf(IllegalArgumentException.class);
    }
}
