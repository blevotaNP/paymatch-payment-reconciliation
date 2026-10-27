package com.paymatch.invoice;

import com.paymatch.matching.MatchingRunResult;

import java.util.List;

public record InvoiceImportResult(int totalRows, int imported, List<SkippedRow> skipped, String detectedEncoding,
                                  MatchingRunResult matching) {

    public record SkippedRow(long line, String invoiceNumber, String reason) {
    }
}
