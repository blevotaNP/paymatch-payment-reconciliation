package com.paymatch.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InvoiceResponse(
        Long id,
        String number,
        CounterpartySummary counterparty,
        LocalDate issueDate,
        LocalDate dueDate,
        BigDecimal grossAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        String currency,
        InvoiceStatus status,
        boolean overdue,
        long daysOverdue,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static InvoiceResponse from(Invoice invoice, LocalDate today) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getNumber(),
                new CounterpartySummary(invoice.getCounterparty().getId(),
                        invoice.getCounterparty().getName(),
                        invoice.getCounterparty().getNip()),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getGrossAmount(),
                invoice.getPaidAmount(),
                invoice.getRemainingAmount(),
                invoice.getCurrency(),
                invoice.getStatus(),
                invoice.isOverdue(today),
                invoice.getDaysOverdue(today),
                invoice.getVersion(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt());
    }

    public record CounterpartySummary(Long id, String name, String nip) {
    }
}
