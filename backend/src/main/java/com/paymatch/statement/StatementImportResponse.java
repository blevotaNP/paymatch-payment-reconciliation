package com.paymatch.statement;

import com.paymatch.statement.parser.SkippedRow;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StatementImportResponse(
        Long id,
        String fileName,
        BankFormat bankFormat,
        String bankLabel,
        String accountIban,
        LocalDate periodFrom,
        LocalDate periodTo,
        String encoding,
        int totalRows,
        int importedCount,
        int duplicateCount,
        int skippedCount,
        List<SkippedRow> skippedRows,
        List<String> warnings,
        ImportStatus status,
        String importedBy,
        Instant importedAt,
        String undoneBy,
        Instant undoneAt,
        /** Only filled in the response to the upload itself; not stored. */
        List<DuplicateRow> duplicates) {

    public static StatementImportResponse from(StatementImport statementImport) {
        return from(statementImport, null);
    }

    public static StatementImportResponse from(StatementImport i, List<DuplicateRow> duplicates) {
        return new StatementImportResponse(
                i.getId(),
                i.getFileName(),
                i.getBankFormat(),
                i.getBankFormat().getLabel(),
                i.getAccountIban(),
                i.getPeriodFrom(),
                i.getPeriodTo(),
                i.getEncoding(),
                i.getTotalRows(),
                i.getImportedCount(),
                i.getDuplicateCount(),
                i.getSkippedCount(),
                i.getSkippedRows(),
                i.getWarnings(),
                i.getStatus(),
                i.getImportedBy().getDisplayName(),
                i.getImportedAt(),
                i.getUndoneBy() != null ? i.getUndoneBy().getDisplayName() : null,
                i.getUndoneAt(),
                duplicates);
    }

    public record DuplicateRow(long line, LocalDate bookingDate, String amount, String title) {
    }
}
