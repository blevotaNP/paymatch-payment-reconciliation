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

    /** Polish notation used in messages shown to users: "1 230,50 zł". */
    public static String format(BigDecimal amount, String currency) {
        String plain = amount.setScale(SCALE, RoundingMode.HALF_UP).abs().toPlainString();
        String integer = plain.substring(0, plain.indexOf('.'));
        String fraction = plain.substring(plain.indexOf('.') + 1);
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < integer.length(); i++) {
            if (i > 0 && (integer.length() - i) % 3 == 0) {
                grouped.append('\u00A0');
            }
            grouped.append(integer.charAt(i));
        }
        String label = "PLN".equals(currency) ? "zł" : currency;
        return (amount.signum() < 0 ? "-" : "") + grouped + "," + fraction + "\u00A0" + label;
    }
}
