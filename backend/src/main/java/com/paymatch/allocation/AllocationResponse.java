package com.paymatch.allocation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AllocationResponse(Long id, Long invoiceId, String invoiceNumber, String counterpartyName,
                                 BigDecimal amount, AllocationSource source, String createdBy, Instant createdAt,
                                 Long transactionId, String note, LocalDate bookingDate, String sender, String title) {

    public static AllocationResponse from(Allocation a) {
        return new AllocationResponse(a.getId(), a.getInvoice().getId(), a.getInvoice().getNumber(),
                a.getInvoice().getCounterparty().getName(), a.getAmount(), a.getSource(),
                a.getCreatedBy() != null ? a.getCreatedBy().getDisplayName() : null, a.getCreatedAt(),
                a.getTransaction().getId(), a.getNote(), a.getTransaction().getBookingDate(),
                a.getTransaction().getCounterpartyName(), a.getTransaction().getTitle());
    }
}
