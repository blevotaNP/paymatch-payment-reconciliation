package com.paymatch.counterparty;

import com.paymatch.common.validation.ValidIban;
import com.paymatch.common.validation.ValidNip;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CounterpartyRequest(
        @NotBlank @Size(max = 255) String name,
        @ValidNip String nip,
        @ValidIban String iban) {
}
