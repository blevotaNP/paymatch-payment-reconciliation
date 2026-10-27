package com.paymatch.statement;

import com.paymatch.allocation.Allocation;
import com.paymatch.allocation.AllocationRepository;
import com.paymatch.allocation.AllocationService;
import com.paymatch.audit.AuditAction;
import com.paymatch.audit.AuditEntityType;
import com.paymatch.audit.AuditService;
import com.paymatch.common.error.BadRequestException;
import com.paymatch.common.error.ConflictException;
import com.paymatch.common.error.NotFoundException;
import com.paymatch.common.parsing.TextDecoder;
import com.paymatch.invoice.Invoice;
import com.paymatch.matching.MatchingRunResult;
import com.paymatch.matching.MatchingService;
import com.paymatch.statement.StatementImportResponse.DuplicateRow;
import com.paymatch.statement.parser.ParsedStatement;
import com.paymatch.statement.parser.ParsedTransaction;
import com.paymatch.statement.parser.StatementParser;
import com.paymatch.transaction.BankTransaction;
import com.paymatch.transaction.BankTransactionRepository;
import com.paymatch.transaction.MatchStatus;
import com.paymatch.user.AppUser;
import com.paymatch.user.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

@Service
public class StatementImportService {

    private static final Logger log = LoggerFactory.getLogger(StatementImportService.class);
    private static final int MAX_REPORTED_DUPLICATES = 200;
    private static final DateTimeFormatter WARSAW_DATE_TIME =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Warsaw"));

    private final StatementParserRegistry parserRegistry;
    private final StatementImportRepository importRepository;
    private final BankTransactionRepository transactionRepository;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final MatchingService matchingService;
    private final AllocationRepository allocationRepository;
    private final AllocationService allocationService;
    private final AuditService auditService;

    public StatementImportService(StatementParserRegistry parserRegistry, StatementImportRepository importRepository,
                                  BankTransactionRepository transactionRepository, CurrentUser currentUser, Clock clock,
                                  MatchingService matchingService, AllocationRepository allocationRepository,
                                  AllocationService allocationService, AuditService auditService) {
        this.parserRegistry = parserRegistry;
        this.importRepository = importRepository;
        this.transactionRepository = transactionRepository;
        this.currentUser = currentUser;
        this.clock = clock;
        this.matchingService = matchingService;
        this.allocationRepository = allocationRepository;
        this.allocationService = allocationService;
        this.auditService = auditService;
    }

    /**
     * Parses the file, drops transactions that are already stored, and saves the rest in one transaction.
     * Two parallel uploads of the same file are stopped by the unique index on dedup_key (one of them fails with 409).
     */
    @Transactional
    public StatementImportResponse importFile(String fileName, byte[] content, BankFormat requestedFormat) {
        return importFile(fileName, content, requestedFormat, null);
    }

    /**
     * @param dateAdjuster optional change applied to every date after parsing; the demo data loader uses it to move
     *                     the sample statements to the current month
     */
    @Transactional
    public StatementImportResponse importFile(String fileName, byte[] content, BankFormat requestedFormat,
                                              UnaryOperator<LocalDate> dateAdjuster) {
        if (content.length == 0) {
            throw new BadRequestException("File is empty");
        }
        TextDecoder.DecodedText decoded = TextDecoder.decode(content);
        StatementParser parser = requestedFormat != null
                ? parserRegistry.get(requestedFormat)
                : parserRegistry.detect(decoded.text()).orElseThrow(() -> new BadRequestException(
                        "Could not recognise the bank format. Choose the bank manually and try again."));

        ParsedStatement parsed = parser.parse(decoded.text());
        if (dateAdjuster != null) {
            parsed.adjustDates(dateAdjuster);
        }
        if (parsed.getTransactions().isEmpty() && parsed.getSkipped().isEmpty()) {
            throw new BadRequestException("No transactions found in the file");
        }

        String fileHash = DedupKeyGenerator.sha256(content);
        List<String> warnings = new ArrayList<>(parsed.getWarnings());
        importRepository.findFirstByFileSha256AndStatusOrderByImportedAtDesc(fileHash, ImportStatus.ACTIVE)
                .ifPresent(previous -> warnings.add("This exact file was already imported on "
                        + WARSAW_DATE_TIME.format(previous.getImportedAt()) + " by " + previous.getImportedBy().getDisplayName()));

        AppUser user = currentUser.get();
        StatementImport statementImport = importRepository.save(
                new StatementImport(fileName, parser.format(), fileHash, decoded.charset().name(), user));

        List<ParsedTransaction> transactions = parsed.getTransactions();
        List<String> keys = DedupKeyGenerator.generate(parser.format(), parsed.getAccountIban(), transactions);
        Set<String> existing = keys.isEmpty() ? Set.of() : transactionRepository.findExistingDedupKeys(keys);
        Set<String> seenInFile = new HashSet<>();

        List<BankTransaction> toSave = new ArrayList<>();
        List<DuplicateRow> duplicates = new ArrayList<>();
        int duplicateCount = 0;
        for (int i = 0; i < transactions.size(); i++) {
            ParsedTransaction tx = transactions.get(i);
            String key = keys.get(i);
            if (existing.contains(key) || !seenInFile.add(key)) {
                duplicateCount++;
                if (duplicates.size() < MAX_REPORTED_DUPLICATES) {
                    duplicates.add(new DuplicateRow(tx.lineNumber(), tx.bookingDate(), tx.amount().toPlainString(), tx.title()));
                }
                continue;
            }
            toSave.add(toEntity(statementImport, parsed.getAccountIban(), tx, key));
        }
        transactionRepository.saveAll(toSave);
        MatchingRunResult matching = matchingService.matchTransactions(toSave);

        LocalDate periodFrom = parsed.getPeriodFrom() != null ? parsed.getPeriodFrom() : minDate(transactions);
        LocalDate periodTo = parsed.getPeriodTo() != null ? parsed.getPeriodTo() : maxDate(transactions);
        statementImport.recordResult(parsed.getAccountIban(), periodFrom, periodTo, parsed.getTotalRows(),
                toSave.size(), duplicateCount, parsed.getSkipped(), warnings);

        auditService.record(AuditAction.IMPORT_CREATED, AuditEntityType.IMPORT, statementImport.getId(),
                "Imported " + fileName + ": " + toSave.size() + " new, " + duplicateCount + " duplicates, "
                        + parsed.getSkipped().size() + " skipped, " + matching.autoConfirmed() + " matched automatically",
                List.of(), Map.of("bankFormat", parser.format().name(), "fileName", fileName));
        log.info("Imported {} ({}): {} new, {} duplicates, {} skipped", fileName, parser.format(),
                toSave.size(), duplicateCount, parsed.getSkipped().size());
        return StatementImportResponse.from(statementImport, duplicates, matching);
    }

    @Transactional(readOnly = true)
    public Page<StatementImportResponse> list(Pageable pageable) {
        return importRepository.findAll(pageable).map(StatementImportResponse::from);
    }

    @Transactional(readOnly = true)
    public StatementImportResponse get(Long id) {
        return StatementImportResponse.from(findImport(id));
    }

    /**
     * Removes all transactions of the import. The import record itself stays, marked as undone, for history.
     * Automatic matches are reverted together with the import; decisions made by a person block the undo,
     * because silently throwing away someone's work is worse than asking them to revert it first.
     */
    @Transactional
    public StatementImportResponse undo(Long id) {
        StatementImport statementImport = findImport(id);
        if (statementImport.getStatus() == ImportStatus.UNDONE) {
            throw new ConflictException("Import was already undone");
        }
        if (allocationRepository.existsManualForImport(id)
                || transactionRepository.existsByStatementImportIdAndMatchStatus(id, MatchStatus.IGNORED)) {
            throw new ConflictException("Some transactions from this import were already reviewed by a person. "
                    + "Undo those matches first, then undo the import.");
        }
        List<Invoice> affectedInvoices = allocationRepository.findByImportId(id).stream()
                .map(Allocation::getInvoice).distinct().toList();
        allocationRepository.deleteByImportId(id);
        // suggestions go with their transactions (ON DELETE CASCADE)
        int deleted = transactionRepository.deleteByImportId(id);
        allocationService.recalculate(affectedInvoices);
        statementImport.markUndone(currentUser.get(), Instant.now(clock));
        auditService.record(AuditAction.IMPORT_UNDONE, AuditEntityType.IMPORT, id,
                "Undid import of " + statementImport.getFileName() + " (" + deleted + " transactions removed)",
                affectedInvoices.stream().map(Invoice::getId).toList(), Map.of("fileName", statementImport.getFileName()));
        log.info("Undid import {} ({} transactions removed)", id, deleted);
        return StatementImportResponse.from(statementImport);
    }

    private StatementImport findImport(Long id) {
        return importRepository.findWithUsersById(id).orElseThrow(() -> NotFoundException.of("Import", id));
    }

    private static BankTransaction toEntity(StatementImport statementImport, String accountIban, ParsedTransaction tx, String key) {
        return new BankTransaction(
                statementImport,
                accountIban,
                tx.bookingDate(),
                tx.valueDate(),
                tx.amount(),
                tx.currency(),
                truncate(tx.counterpartyName(), 255),
                truncate(tx.counterpartyAccount(), 34),
                truncate(tx.title(), 1000),
                truncate(tx.bankReference(), 100),
                key,
                tx.rawData());
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static LocalDate minDate(List<ParsedTransaction> transactions) {
        return transactions.stream().map(ParsedTransaction::bookingDate).min(Comparator.naturalOrder()).orElse(null);
    }

    private static LocalDate maxDate(List<ParsedTransaction> transactions) {
        return transactions.stream().map(ParsedTransaction::bookingDate).max(Comparator.naturalOrder()).orElse(null);
    }
}
