package com.paymatch.statement;

import com.paymatch.statement.parser.SkippedRow;
import com.paymatch.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "statement_import")
public class StatementImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BankFormat bankFormat;

    private String accountIban;
    private LocalDate periodFrom;
    private LocalDate periodTo;

    @Column(name = "file_sha256", nullable = false)
    private String fileSha256;

    @Column(nullable = false)
    private String encoding;

    private int totalRows;
    private int importedCount;
    private int duplicateCount;
    private int skippedCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<SkippedRow> skippedRows = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> warnings = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportStatus status = ImportStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imported_by")
    private AppUser importedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant importedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "undone_by")
    private AppUser undoneBy;

    private Instant undoneAt;

    protected StatementImport() {
    }

    public StatementImport(String fileName, BankFormat bankFormat, String fileSha256, String encoding, AppUser importedBy) {
        this.fileName = fileName;
        this.bankFormat = bankFormat;
        this.fileSha256 = fileSha256;
        this.encoding = encoding;
        this.importedBy = importedBy;
    }

    public void recordResult(String accountIban, LocalDate periodFrom, LocalDate periodTo, int totalRows,
                             int importedCount, int duplicateCount, List<SkippedRow> skippedRows, List<String> warnings) {
        this.accountIban = accountIban;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.totalRows = totalRows;
        this.importedCount = importedCount;
        this.duplicateCount = duplicateCount;
        this.skippedCount = skippedRows.size();
        this.skippedRows = new ArrayList<>(skippedRows);
        this.warnings = new ArrayList<>(warnings);
    }

    public void markUndone(AppUser user, Instant when) {
        this.status = ImportStatus.UNDONE;
        this.undoneBy = user;
        this.undoneAt = when;
    }

    public Long getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public BankFormat getBankFormat() {
        return bankFormat;
    }

    public String getAccountIban() {
        return accountIban;
    }

    public LocalDate getPeriodFrom() {
        return periodFrom;
    }

    public LocalDate getPeriodTo() {
        return periodTo;
    }

    public String getFileSha256() {
        return fileSha256;
    }

    public String getEncoding() {
        return encoding;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getImportedCount() {
        return importedCount;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public List<SkippedRow> getSkippedRows() {
        return skippedRows;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public AppUser getImportedBy() {
        return importedBy;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public AppUser getUndoneBy() {
        return undoneBy;
    }

    public Instant getUndoneAt() {
        return undoneAt;
    }
}
