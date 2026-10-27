package com.paymatch.common.parsing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmountParserTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1230.50       | 1230.50",
            "1230,50       | 1230.50",
            "1 230,50      | 1230.50",
            "1 230,50 | 1230.50",
            "1.230,50      | 1230.50",
            "1,230.50      | 1230.50",
            "1.230.500     | 1230500.00",
            "-45,00        | -45.00",
            "+1 000,00 PLN | 1000.00",
            "250 zł        | 250.00",
            "0,5           | 0.50",
            "100           | 100.00"
    })
    void parsesCommonNotations(String raw, String expected) {
        BigDecimal amount = AmountParser.parse(raw);

        assertThat(amount).isEqualTo(new BigDecimal(expected));
        assertThat(amount.scale()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "abc", "12,3,4", "1 230,505", "--5"})
    void rejectsInvalidAmounts(String raw) {
        assertThatThrownBy(() -> AmountParser.parse(raw)).isInstanceOf(IllegalArgumentException.class);
    }
}
