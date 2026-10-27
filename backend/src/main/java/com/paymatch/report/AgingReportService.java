package com.paymatch.report;

import com.paymatch.common.Money;
import com.paymatch.invoice.Invoice;
import com.paymatch.invoice.InvoiceRepository;
import com.paymatch.invoice.InvoiceStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Always "as of today": paid amounts are kept as current totals, so an aging report for a past date would mix
 * today's payments with yesterday's due dates. A historical report would need the allocation dates, see README.
 */
@Service
public class AgingReportService {

    private final InvoiceRepository invoiceRepository;
    private final Clock clock;

    public AgingReportService(InvoiceRepository invoiceRepository, Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AgingReport build(String currency) {
        LocalDate today = LocalDate.now(clock);
        List<Invoice> open = invoiceRepository.findOpenWithCounterparty(
                List.of(InvoiceStatus.UNPAID, InvoiceStatus.PARTIALLY_PAID), currency);

        Map<Long, List<Invoice>> byCounterparty = new LinkedHashMap<>();
        for (Invoice invoice : open) {
            byCounterparty.computeIfAbsent(invoice.getCounterparty().getId(), id -> new ArrayList<>()).add(invoice);
        }

        Map<AgingBucket, BigDecimal> totals = emptyBuckets();
        List<AgingReport.Row> rows = new ArrayList<>();
        for (List<Invoice> invoices : byCounterparty.values()) {
            Map<AgingBucket, BigDecimal> buckets = emptyBuckets();
            List<AgingReport.InvoiceLine> lines = new ArrayList<>();
            for (Invoice invoice : invoices) {
                long days = invoice.getDaysOverdue(today);
                AgingBucket bucket = AgingBucket.forDaysOverdue(days);
                BigDecimal remaining = invoice.getRemainingAmount();
                buckets.merge(bucket, remaining, BigDecimal::add);
                totals.merge(bucket, remaining, BigDecimal::add);
                lines.add(new AgingReport.InvoiceLine(invoice.getId(), invoice.getNumber(), invoice.getDueDate(), days,
                        bucket, invoice.getGrossAmount(), remaining));
            }
            lines.sort(Comparator.comparing(AgingReport.InvoiceLine::dueDate));
            var counterparty = invoices.getFirst().getCounterparty();
            rows.add(new AgingReport.Row(counterparty.getId(), counterparty.getName(), counterparty.getNip(),
                    buckets, sum(buckets), lines));
        }
        // the most overdue money first: that is who to call
        rows.sort(Comparator.comparing((AgingReport.Row row) -> overdue(row.buckets())).reversed()
                .thenComparing(AgingReport.Row::counterpartyName));
        return new AgingReport(today, currency, rows, totals, sum(totals));
    }

    private static Map<AgingBucket, BigDecimal> emptyBuckets() {
        Map<AgingBucket, BigDecimal> buckets = new EnumMap<>(AgingBucket.class);
        for (AgingBucket bucket : AgingBucket.values()) {
            buckets.put(bucket, Money.ZERO);
        }
        return buckets;
    }

    private static BigDecimal sum(Map<AgingBucket, BigDecimal> buckets) {
        return buckets.values().stream().reduce(Money.ZERO, BigDecimal::add);
    }

    private static BigDecimal overdue(Map<AgingBucket, BigDecimal> buckets) {
        return sum(buckets).subtract(buckets.get(AgingBucket.NOT_DUE));
    }
}
