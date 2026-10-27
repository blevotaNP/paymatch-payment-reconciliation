package com.paymatch.statement;

public enum BankFormat {
    MT940("MT940", "SWIFT MT940 statement, exported by most Polish banks (.sta, .mt940, .txt)"),
    PKO_BP_CSV("PKO BP (CSV)", "iPKO / iPKO biznes \"Historia rachunku\" export"),
    MBANK_CSV("mBank (CSV)", "mBank \"Lista operacji\" export"),
    ING_CSV("ING (CSV)", "ING Business \"Lista transakcji\" export");

    private final String label;
    private final String description;

    BankFormat(String label, String description) {
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
