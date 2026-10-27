package com.paymatch.invoice;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceRequest(
        @NotBlank @Size(max = 64) String number,
        @NotNull Long counterpartyId,
        @NotNull LocalDate issueDate,
        @NotNull LocalDate dueDate,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal grossAmount,
        @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter currency code") String currency) {

    @Schema(hidden = true)
    @AssertTrue(message = "due date cannot be before issue date")
    public boolean isDueDateValid() {
        return issueDate == null || dueDate == null || !dueDate.isBefore(issueDate);
    }

    public String currencyOrDefault() {
        return currency == null ? "PLN" : currency;
    }
}
