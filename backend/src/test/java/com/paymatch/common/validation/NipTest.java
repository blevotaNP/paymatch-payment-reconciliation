package com.paymatch.common.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class NipTest {

    @ParameterizedTest
    @ValueSource(strings = {"5260181596", "526-018-15-96", "526 018 15 96", "PL5260181596", "pl 526-018-15-96"})
    void acceptsValidNipInCommonNotations(String nip) {
        assertThat(Nip.isValid(nip)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"5260181597", "526018159", "52601815960", "abcdefghij", "", "0000000000x"})
    void rejectsInvalidNip(String nip) {
        assertThat(Nip.isValid(nip)).isFalse();
    }

    @Test
    void rejectsNipWhoseChecksumWouldBeTen() {
        // 1*6+2*5+3*7+4*2+5*3+6*4+7*5+8*6+9*7 = 230, 230 % 11 = 10, which is never a valid checksum
        assertThat(Nip.isValid("1234567890")).isFalse();
    }

    @Test
    void normalizesToDigitsOnly() {
        assertThat(Nip.normalize("PL 526-018-15-96")).isEqualTo("5260181596");
    }
}
