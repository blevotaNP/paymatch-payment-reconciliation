package com.paymatch.statement;

public record BankFormatResponse(BankFormat code, String label, String description) {

    static BankFormatResponse from(BankFormat format) {
        return new BankFormatResponse(format, format.getLabel(), format.getDescription());
    }
}
