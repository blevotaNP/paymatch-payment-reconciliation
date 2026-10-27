package com.paymatch.counterparty;

public record CounterpartyResponse(Long id, String name, String nip, String iban) {

    public static CounterpartyResponse from(Counterparty counterparty) {
        return new CounterpartyResponse(counterparty.getId(), counterparty.getName(),
                counterparty.getNip(), counterparty.getIban());
    }
}
