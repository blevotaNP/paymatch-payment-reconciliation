package com.paymatch.invoice;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceStatusTest {

    @ParameterizedTest
    @CsvSource({
            "1230.50, 0.00, UNPAID",
            "1230.50, 0.01, PARTIALLY_PAID",
            "1230.50, 1230.49, PARTIALLY_PAID",
            "1230.50, 1230.50, PAID",
            "1230.50, 1230.5, PAID",
            "1230.50, 1230.51, OVERPAID"
    })
    void derivesStatusFromPaidAmount(String gross, String paid, InvoiceStatus expected) {
        assertThat(InvoiceStatus.from(new BigDecimal(gross), new BigDecimal(paid))).isEqualTo(expected);
    }
}
