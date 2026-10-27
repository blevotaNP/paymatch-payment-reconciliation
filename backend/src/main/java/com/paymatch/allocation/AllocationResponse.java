package com.paymatch.allocation;

import java.math.BigDecimal;
import java.time.Instant;

public record AllocationResponse(Long id, Long invoiceId, String invoiceNumber, String counterpartyName,
                                 BigDecimal amount, AllocationSource source, String createdBy, Instant createdAt,
                                 Long transactionId, String note) {

    public static AllocationResponse from(Allocation a) {
        return new AllocationResponse(a.getId(), a.getInvoice().getId(), a.getInvoice().getNumber(),
                a.getInvoice().getCounterparty().getName(), a.getAmount(), a.getSource(),
                a.getCreatedBy() != null ? a.getCreatedBy().getDisplayName() : null, a.getCreatedAt(),
                a.getTransaction().getId(), a.getNote());
    }
}
