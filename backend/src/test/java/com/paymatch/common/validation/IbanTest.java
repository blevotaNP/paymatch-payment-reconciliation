package com.paymatch.common.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IbanTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "PL61109010140000071219812874",
            "PL61 1090 1014 0000 0712 1981 2874",
            "pl61109010140000071219812874",
            "61109010140000071219812874",
            "61 1090 1014 0000 0712 1981 2874",
            "DE89370400440532013000"
    })
    void acceptsValidIban(String iban) {
        assertThat(Iban.isValid(iban)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "PL62109010140000071219812874",
            "PL6110901014000007121981287",
            "PL611090101400000712198128745",
            "XX",
            "1090101400000712198128"
    })
    void rejectsInvalidIban(String iban) {
        assertThat(Iban.isValid(iban)).isFalse();
    }

    @Test
    void formatsInGroupsOfFour() {
        assertThat(Iban.format("61109010140000071219812874")).isEqualTo("PL61 1090 1014 0000 0712 1981 2874");
    }

    @Test
    void addsPlPrefixToBareNrb() {
        assertThat(Iban.normalize("61 1090 1014 0000 0712 1981 2874")).isEqualTo("PL61109010140000071219812874");
    }
}
