package com.paymatch.export;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/exports")
@Tag(name = "Exports")
public class ExportController {

    private final ExportService exportService;
    private final CsvExporter csvExporter;
    private final XlsxExporter xlsxExporter;
    private final Clock clock;

    public ExportController(ExportService exportService, CsvExporter csvExporter, XlsxExporter xlsxExporter, Clock clock) {
        this.exportService = exportService;
        this.csvExporter = csvExporter;
        this.xlsxExporter = xlsxExporter;
        this.clock = clock;
    }

    @GetMapping("/reconciliation")
    @Operation(summary = "Incoming payments with their matched invoices; XLSX also contains an Invoices sheet")
    public ResponseEntity<byte[]> reconciliation(
            @RequestParam(defaultValue = "XLSX") ExportFormat format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ExportTable payments = exportService.reconciliation(from, to);
        byte[] body = format == ExportFormat.CSV
                ? csvExporter.write(payments)
                : xlsxExporter.write(List.of(payments, exportService.invoices()));
        return file("reconciliation", format, body);
    }

    @GetMapping("/invoices")
    public ResponseEntity<byte[]> invoices(@RequestParam(defaultValue = "XLSX") ExportFormat format) {
        ExportTable table = exportService.invoices();
        return file("invoices", format, format == ExportFormat.CSV ? csvExporter.write(table) : xlsxExporter.write(List.of(table)));
    }

    @GetMapping("/aging")
    public ResponseEntity<byte[]> aging(@RequestParam(defaultValue = "XLSX") ExportFormat format,
                                        @RequestParam(defaultValue = "PLN") String currency) {
        ExportTable table = exportService.aging(currency);
        return file("aging", format, format == ExportFormat.CSV ? csvExporter.write(table) : xlsxExporter.write(List.of(table)));
    }

    private ResponseEntity<byte[]> file(String name, ExportFormat format, byte[] body) {
        String fileName = "paymatch-" + name + "-" + LocalDate.now(clock) + "." + format.getExtension();
        return ResponseEntity.ok()
                .contentType(format.getMediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(body);
    }
}
