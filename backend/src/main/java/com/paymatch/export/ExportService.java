package com.paymatch.export;

import com.paymatch.allocation.Allocation;
import com.paymatch.allocation.AllocationRepository;
import com.paymatch.allocation.AllocationSource;
import com.paymatch.invoice.Invoice;
import com.paymatch.invoice.InvoiceRepository;
import com.paymatch.report.AgingBucket;
import com.paymatch.report.AgingReport;
import com.paymatch.report.AgingReportService;
import com.paymatch.transaction.BankTransaction;
import com.paymatch.transaction.BankTransactionRepository;
import com.paymatch.transaction.MatchStatus;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.paymatch.export.ExportTable.Type.DATE;
import static com.paymatch.export.ExportTable.Type.MONEY;
import static com.paymatch.export.ExportTable.Type.NUMBER;
import static com.paymatch.export.ExportTable.Type.TEXT;

@Service
public class ExportService {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Europe/Warsaw"));

    private final BankTransactionRepository transactionRepository;
    private final AllocationRepository allocationRepository;
    private final InvoiceRepository invoiceRepository;
    private final AgingReportService agingReportService;
    private final Clock clock;

    public ExportService(BankTransactionRepository transactionRepository, AllocationRepository allocationRepository,
                         InvoiceRepository invoiceRepository, AgingReportService agingReportService, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.allocationRepository = allocationRepository;
        this.invoiceRepository = invoiceRepository;
        this.agingReportService = agingReportService;
        this.clock = clock;
    }

    /**
     * One row per allocation, so a split payment appears once per invoice and the allocated amounts add up
     * per invoice. Payments without an allocation get one row with the invoice columns empty.
     */
    @Transactional(readOnly = true)
    public ExportTable reconciliation(LocalDate from, LocalDate to) {
        ExportTable table = new ExportTable("Payments")
                .column("Booking date", DATE, 12)
                .column("Amount", MONEY, 14)
                .column("Currency", TEXT, 8)
                .column("Sender", TEXT, 36)
                .column("Sender account", TEXT, 32)
                .column("Title", TEXT, 44)
                .column("Status", TEXT, 16)
                .column("Invoice", TEXT, 16)
                .column("Allocated", MONEY, 14)
                .column("Matched by", TEXT, 18)
                .column("Matched at", TEXT, 17)
                .column("Note", TEXT, 36)
                .column("Bank reference", TEXT, 22)
                .column("Source file", TEXT, 34);

        List<BankTransaction> transactions = transactionRepository.findIncomingBetween(from, to);
        Map<Long, List<Allocation>> allocations = transactions.isEmpty() ? Map.of()
                : allocationRepository.findByTransactionIdIn(transactions.stream().map(BankTransaction::getId).toList()).stream()
                .collect(Collectors.groupingBy(a -> a.getTransaction().getId()));

        for (BankTransaction tx : transactions) {
            List<Allocation> parts = allocations.getOrDefault(tx.getId(), List.of());
            if (parts.isEmpty()) {
                String note = tx.getMatchStatus() == MatchStatus.IGNORED
                        ? tx.getIgnoreReason().getLabel() + (tx.getIgnoreNote() != null ? ": " + tx.getIgnoreNote() : "")
                        : null;
                table.row(tx.getBookingDate(), tx.getAmount(), tx.getCurrency(), tx.getCounterpartyName(),
                        tx.getCounterpartyAccount(), tx.getTitle(), statusLabel(tx.getMatchStatus()), null, null, null, null,
                        note, tx.getBankReference(), tx.getStatementImport().getFileName());
            }
            for (Allocation a : parts) {
                String matchedBy = a.getSource() == AllocationSource.AUTO ? "Automatic"
                        : a.getCreatedBy() != null ? a.getCreatedBy().getDisplayName() : "Manual";
                table.row(tx.getBookingDate(), tx.getAmount(), tx.getCurrency(), tx.getCounterpartyName(),
                        tx.getCounterpartyAccount(), tx.getTitle(), statusLabel(tx.getMatchStatus()), a.getInvoice().getNumber(),
                        a.getAmount(), matchedBy, TIMESTAMP.format(a.getCreatedAt()), a.getNote(), tx.getBankReference(),
                        tx.getStatementImport().getFileName());
            }
        }
        return table;
    }

    @Transactional(readOnly = true)
    public ExportTable invoices() {
        LocalDate today = LocalDate.now(clock);
        ExportTable table = new ExportTable("Invoices")
                .column("Number", TEXT, 16)
                .column("Counterparty", TEXT, 36)
                .column("NIP", TEXT, 12)
                .column("Issue date", DATE, 12)
                .column("Due date", DATE, 12)
                .column("Gross", MONEY, 14)
                .column("Paid", MONEY, 14)
                .column("Remaining", MONEY, 14)
                .column("Currency", TEXT, 8)
                .column("Status", TEXT, 15)
                .column("Days overdue", NUMBER, 12);
        for (Invoice i : invoiceRepository.findAllWithCounterparty(Sort.by("dueDate", "number"))) {
            table.row(i.getNumber(), i.getCounterparty().getName(), i.getCounterparty().getNip(), i.getIssueDate(),
                    i.getDueDate(), i.getGrossAmount(), i.getPaidAmount(), i.getRemainingAmount(), i.getCurrency(),
                    i.getStatus().name().replace('_', ' ').toLowerCase(), i.getDaysOverdue(today));
        }
        return table;
    }

    @Transactional(readOnly = true)
    public ExportTable aging(String currency) {
        AgingReport report = agingReportService.build(currency);
        ExportTable table = new ExportTable("Aging " + report.asOf())
                .column("Counterparty", TEXT, 36)
                .column("NIP", TEXT, 12);
        for (AgingBucket bucket : AgingBucket.values()) {
            table.column(bucket.getLabel(), MONEY, 14);
        }
        table.column("Total", MONEY, 14);

        for (AgingReport.Row row : report.rows()) {
            List<Object> values = new ArrayList<>(List.of(row.counterpartyName()));
            values.add(row.nip());
            for (AgingBucket bucket : AgingBucket.values()) {
                values.add(row.buckets().get(bucket));
            }
            values.add(row.total());
            table.row(values.toArray());
        }
        List<Object> totals = new ArrayList<>();
        totals.add("Total " + currency);
        totals.add(null);
        for (AgingBucket bucket : AgingBucket.values()) {
            totals.add(report.totals().get(bucket));
        }
        totals.add(report.total());
        table.totalRow(totals.toArray());
        return table;
    }

    private static String statusLabel(MatchStatus status) {
        return switch (status) {
            case MATCHED -> "matched";
            case SUGGESTED -> "to review";
            case UNMATCHED -> "unmatched";
            case IGNORED -> "not an invoice";
        };
    }
}
