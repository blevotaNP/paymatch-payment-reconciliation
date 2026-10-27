package com.paymatch.matching;

public enum MatchRule {
    EXACT("Exact match", "Invoice number in the title and the amount equals what is left to pay"),
    PARTIAL_PAYMENT("Partial payment", "Invoice number in the title, but the amount is lower than what is left to pay"),
    OVERPAYMENT("Overpayment", "Invoice number in the title, but the amount is higher than what is left to pay"),
    ALREADY_PAID("Invoice already paid", "Invoice number in the title, but the invoice is already paid; possibly a double payment"),
    MULTIPLE_INVOICES("Several invoice numbers", "Several invoice numbers in the title and the amount equals their sum"),
    COUNTERPARTY_AMOUNT("Known payer, same amount", "Payer recognised by account, NIP or name, and the amount equals an open invoice"),
    COMBINED_PAYMENT("Combined payment", "Payer recognised and the amount equals the sum of several of their open invoices");

    private final String label;
    private final String description;

    MatchRule(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
