package com.paymatch.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    public static final int SCALE = 2;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {
    }

    /**
     * Brings an amount to scale 2. Fails on more than two decimal places instead of silently rounding,
     * because an amount like 10.005 in an input file means the file is wrong.
     */
    public static BigDecimal of(BigDecimal amount) {
        try {
            return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount has more than " + SCALE + " decimal places: " + amount.toPlainString());
        }
    }

    public static BigDecimal of(String amount) {
        return of(new BigDecimal(amount));
    }
}
