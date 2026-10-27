package com.paymatch.invoice;

import java.util.List;

public record InvoiceImportResult(int totalRows, int imported, List<SkippedRow> skipped, String detectedEncoding) {

    public record SkippedRow(long line, String invoiceNumber, String reason) {
    }
}
