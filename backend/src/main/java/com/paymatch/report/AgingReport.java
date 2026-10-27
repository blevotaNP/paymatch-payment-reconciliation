package com.paymatch.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Open receivables grouped by counterparty and by how long they are overdue. */
public record AgingReport(LocalDate asOf, String currency, List<Row> rows, Map<AgingBucket, BigDecimal> totals, BigDecimal total) {

    public record Row(Long counterpartyId, String counterpartyName, String nip, Map<AgingBucket, BigDecimal> buckets,
                      BigDecimal total, List<InvoiceLine> invoices) {
    }

    public record InvoiceLine(Long invoiceId, String number, LocalDate dueDate, long daysOverdue, AgingBucket bucket,
                              BigDecimal grossAmount, BigDecimal remainingAmount) {
    }
}
