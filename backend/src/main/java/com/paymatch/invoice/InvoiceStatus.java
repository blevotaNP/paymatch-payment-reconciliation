package com.paymatch.invoice;

import java.math.BigDecimal;

public enum InvoiceStatus {
    UNPAID,
    PARTIALLY_PAID,
    PAID,
    OVERPAID;

    public static InvoiceStatus from(BigDecimal grossAmount, BigDecimal paidAmount) {
        if (paidAmount.signum() == 0) {
            return UNPAID;
        }
        int comparison = paidAmount.compareTo(grossAmount);
        if (comparison < 0) {
            return PARTIALLY_PAID;
        }
        return comparison == 0 ? PAID : OVERPAID;
    }

    public boolean isOpen() {
        return this == UNPAID || this == PARTIALLY_PAID;
    }
}
