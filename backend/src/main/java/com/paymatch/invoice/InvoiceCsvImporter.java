package com.paymatch.invoice;

import com.paymatch.audit.AuditAction;
import com.paymatch.audit.AuditEntityType;
import com.paymatch.audit.AuditService;
import com.paymatch.common.error.BadRequestException;
import com.paymatch.common.parsing.AmountParser;
import com.paymatch.common.parsing.CsvSupport;
import com.paymatch.common.parsing.DateParser;
import com.paymatch.common.parsing.TextDecoder;
import com.paymatch.common.validation.Iban;
import com.paymatch.common.validation.Nip;
import com.paymatch.counterparty.Counterparty;
import com.paymatch.counterparty.CounterpartyService;
import com.paymatch.invoice.InvoiceImportResult.SkippedRow;
import com.paymatch.matching.MatchingRunResult;
import com.paymatch.matching.MatchingService;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Imports invoices from CSV. Expected columns (header names are case-insensitive, order does not matter):
 * number, counterparty_name, nip, iban, issue_date, due_date, gross_amount, currency.
 * nip, iban and currency are optional. Invalid rows are skipped and reported, valid rows are imported.
 */
@Service
public class InvoiceCsvImporter {

    private static final List<String> REQUIRED_COLUMNS =
            List.of("number", "counterparty_name", "issue_date", "due_date", "gross_amount");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    private final InvoiceRepository invoiceRepository;
    private final CounterpartyService counterpartyService;
    private final MatchingService matchingService;
    private final AuditService auditService;

    public InvoiceCsvImporter(InvoiceRepository invoiceRepository, CounterpartyService counterpartyService,
                              MatchingService matchingService, AuditService auditService) {
        this.invoiceRepository = invoiceRepository;
        this.counterpartyService = counterpartyService;
        this.matchingService = matchingService;
        this.auditService = auditService;
    }

    @Transactional
    public InvoiceImportResult importCsv(byte[] content) {
        if (content.length == 0) {
            throw new BadRequestException("File is empty");
        }
        TextDecoder.DecodedText decoded = TextDecoder.decode(content);
        String text = decoded.text();
        char delimiter = CsvSupport.detectDelimiter(CsvSupport.firstLine(text));

        List<SkippedRow> skipped = new ArrayList<>();
        Set<String> numbersInFile = new HashSet<>();
        int total = 0;
        int imported = 0;

        try (CSVParser parser = CsvSupport.format(delimiter).builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setAllowMissingColumnNames(true)
                .get()
                .parse(new StringReader(text))) {

            checkHeader(parser.getHeaderMap());

            for (CSVRecord record : parser) {
                total++;
                String number = value(record, "number");
                try {
                    InvoiceRow row = parseRow(record);
                    String key = row.number().toUpperCase(Locale.ROOT);
                    if (!numbersInFile.add(key)) {
                        throw new IllegalArgumentException("Duplicate invoice number in file");
                    }
                    if (invoiceRepository.existsByNumberIgnoreCase(row.number())) {
                        throw new IllegalArgumentException("Invoice already exists");
                    }
                    Counterparty counterparty = counterpartyService.findOrCreate(row.counterpartyName(), row.nip(), row.iban());
                    invoiceRepository.save(new Invoice(row.number(), counterparty, row.issueDate(), row.dueDate(),
                            row.grossAmount(), row.currency()));
                    imported++;
                } catch (IllegalArgumentException e) {
                    // +1 for the header row
                    skipped.add(new SkippedRow(record.getRecordNumber() + 1, number, e.getMessage()));
                }
            }
        } catch (IOException | IllegalStateException | IllegalArgumentException e) {
            throw new BadRequestException("Could not read CSV file: " + e.getMessage());
        }

        if (imported > 0) {
            auditService.record(AuditAction.INVOICES_IMPORTED, AuditEntityType.INVOICE, null,
                    "Imported " + imported + " invoices from CSV (" + skipped.size() + " rows skipped)", List.of(), Map.of());
        }
        // payments for the new invoices may already be among imported transactions
        MatchingRunResult matching = imported > 0 ? matchingService.rematchOpenTransactions() : MatchingRunResult.empty();
        return new InvoiceImportResult(total, imported, skipped, decoded.charset().name(), matching);
    }

    private void checkHeader(Map<String, Integer> headerMap) {
        Set<String> columns = new HashSet<>();
        headerMap.keySet().forEach(column -> columns.add(column.toLowerCase(Locale.ROOT)));
        List<String> missing = REQUIRED_COLUMNS.stream().filter(column -> !columns.contains(column)).toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("Missing required columns: " + String.join(", ", missing));
        }
    }

    private InvoiceRow parseRow(CSVRecord record) {
        String number = required(record, "number");
        if (number.length() > 64) {
            throw new IllegalArgumentException("Invoice number is longer than 64 characters");
        }
        String name = required(record, "counterparty_name");
        String nip = value(record, "nip");
        if (StringUtils.hasText(nip) && !Nip.isValid(nip)) {
            throw new IllegalArgumentException("Invalid NIP: " + nip);
        }
        String iban = value(record, "iban");
        if (StringUtils.hasText(iban) && !Iban.isValid(iban)) {
            throw new IllegalArgumentException("Invalid IBAN: " + iban);
        }
        LocalDate issueDate = DateParser.parse(required(record, "issue_date"));
        LocalDate dueDate = DateParser.parse(required(record, "due_date"));
        if (dueDate.isBefore(issueDate)) {
            throw new IllegalArgumentException("Due date is before issue date");
        }
        BigDecimal amount = AmountParser.parse(required(record, "gross_amount"));
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Gross amount must be positive");
        }
        String currency = StringUtils.hasText(value(record, "currency"))
                ? value(record, "currency").toUpperCase(Locale.ROOT)
                : "PLN";
        if (!CURRENCY.matcher(currency).matches()) {
            throw new IllegalArgumentException("Invalid currency: " + currency);
        }
        return new InvoiceRow(number, name, nip, iban, issueDate, dueDate, amount, currency);
    }

    private static String required(CSVRecord record, String column) {
        String value = value(record, column);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Missing " + column);
        }
        return value;
    }

    private static String value(CSVRecord record, String column) {
        return record.isMapped(column) && record.isSet(column) ? record.get(column).trim() : null;
    }

    private record InvoiceRow(String number, String counterpartyName, String nip, String iban,
                              LocalDate issueDate, LocalDate dueDate, BigDecimal grossAmount, String currency) {
    }
}
