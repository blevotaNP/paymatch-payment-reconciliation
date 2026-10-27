package com.paymatch.transaction;

/** Why an incoming transfer is not a payment for any invoice. */
public enum IgnoreReason {
    REFUND("Refund from a supplier"),
    BANK_FEE("Bank fee or interest"),
    INTERNAL_TRANSFER("Transfer between own accounts"),
    LOAN_OR_CAPITAL("Loan, grant or capital"),
    OTHER("Other");

    private final String label;

    IgnoreReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
