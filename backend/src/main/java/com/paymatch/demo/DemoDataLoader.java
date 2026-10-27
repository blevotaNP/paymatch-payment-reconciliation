package com.paymatch.demo;

import com.paymatch.common.parsing.TextDecoder;
import com.paymatch.invoice.InvoiceCsvImporter;
import com.paymatch.invoice.InvoiceRepository;
import com.paymatch.matching.MatchSuggestion;
import com.paymatch.matching.MatchSuggestionRepository;
import com.paymatch.matching.SuggestionStatus;
import com.paymatch.review.ReviewRequests;
import com.paymatch.review.ReviewService;
import com.paymatch.statement.StatementImportRepository;
import com.paymatch.statement.StatementImportService;
import com.paymatch.transaction.BankTransaction;
import com.paymatch.transaction.BankTransactionRepository;
import com.paymatch.transaction.IgnoreReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills an empty database with the sample invoices and statements, so the app has something to show on first start.
 * Everything goes through the normal import, matching and review code. The samples are from September and October 2026;
 * their dates are moved by whole months so the latest payments always fall in the current month.
 * Invoice numbers keep their original numbering.
 */
@Component
@Order(2)
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    private static final YearMonth SAMPLES_MONTH = YearMonth.of(2026, 10);
    private static final Pattern CSV_DATE = Pattern.compile("(\\d{2})\\.(\\d{2})\\.(\\d{4})");
    private static final DateTimeFormatter CSV_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final List<String> STATEMENTS = List.of(
            "mt940_pekao_2026-09.sta",
            "mt940_pekao_2026-09-25_2026-10-10.sta",
            "pko_bp_historia_2026-10.csv",
            "mbank_lista_operacji_2026-10.csv",
            "ing_lista_transakcji_2026-10.csv");

    private final DemoDataProperties properties;
    private final InvoiceRepository invoiceRepository;
    private final StatementImportRepository importRepository;
    private final InvoiceCsvImporter invoiceImporter;
    private final StatementImportService statementImportService;
    private final BankTransactionRepository transactionRepository;
    private final MatchSuggestionRepository suggestionRepository;
    private final ReviewService reviewService;
    private final Clock clock;

    public DemoDataLoader(DemoDataProperties properties, InvoiceRepository invoiceRepository,
                          StatementImportRepository importRepository, InvoiceCsvImporter invoiceImporter,
                          StatementImportService statementImportService, BankTransactionRepository transactionRepository,
                          MatchSuggestionRepository suggestionRepository, ReviewService reviewService, Clock clock) {
        this.properties = properties;
        this.invoiceRepository = invoiceRepository;
        this.importRepository = importRepository;
        this.invoiceImporter = invoiceImporter;
        this.statementImportService = statementImportService;
        this.transactionRepository = transactionRepository;
        this.suggestionRepository = suggestionRepository;
        this.reviewService = reviewService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!properties.enabled() || invoiceRepository.count() > 0 || importRepository.count() > 0) {
            return;
        }
        Path directory = Path.of(properties.directory());
        if (!Files.isDirectory(directory.resolve("statements"))) {
            log.warn("Demo data directory {} not found, starting with an empty database", directory.toAbsolutePath());
            return;
        }

        long months = ChronoUnit.MONTHS.between(SAMPLES_MONTH, YearMonth.now(clock));
        log.info("Loading demo data from {} (dates moved by {} months)", directory.toAbsolutePath(), months);
        actingAs("anna", () -> {
            invoiceImporter.importCsv(shiftedInvoices(Files.readAllBytes(directory.resolve("invoices.csv")), months));
            for (String statement : STATEMENTS) {
                statementImportService.importFile(statement, Files.readAllBytes(directory.resolve("statements").resolve(statement)),
                        null, date -> date.plusMonths(months));
            }
            // a couple of decisions, so the dashboard and the activity log are not empty
            confirmTopSuggestion("Zaplata za fv 2/09/2026 czesc 1");
            confirmTopSuggestion("FV/2/09/2026 doplata");
        });
        actingAs("piotr", () -> ignore("Zwrot nadplaty za usluge hostingowa", "Overpaid hosting invoice, returned by the supplier"));
        log.info("Demo data loaded");
    }

    /** The invoice CSV only uses dd.MM.yyyy dates, so a text replace is enough. */
    private static byte[] shiftedInvoices(byte[] content, long months) {
        String text = TextDecoder.decode(content).text();
        Matcher m = CSV_DATE.matcher(text);
        StringBuilder shifted = new StringBuilder();
        while (m.find()) {
            LocalDate date = LocalDate.parse(m.group(), CSV_DATE_FORMAT).plusMonths(months);
            m.appendReplacement(shifted, date.format(CSV_DATE_FORMAT));
        }
        m.appendTail(shifted);
        return shifted.toString().getBytes(TextDecoder.WINDOWS_1250);
    }

    private void confirmTopSuggestion(String title) {
        BankTransaction tx = transaction(title);
        suggestionRepository.findByTransactionIdOrderByScoreDesc(tx.getId()).stream()
                .filter(s -> s.getStatus() == SuggestionStatus.PENDING)
                .map(MatchSuggestion::getId)
                .findFirst()
                .ifPresent(id -> reviewService.confirm(tx.getId(), new ReviewRequests.Confirm(id, tx.getVersion())));
    }

    private void ignore(String title, String note) {
        BankTransaction tx = transaction(title);
        reviewService.ignore(tx.getId(), new ReviewRequests.Ignore(IgnoreReason.REFUND, note, tx.getVersion()));
    }

    private BankTransaction transaction(String title) {
        return transactionRepository.findAll().stream()
                .filter(tx -> title.equals(tx.getTitle()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Demo transaction not found: " + title));
    }

    private static void actingAs(String username, DemoStep step) throws IOException {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, null, List.of()));
        try {
            step.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @FunctionalInterface
    private interface DemoStep {
        void run() throws IOException;
    }
}
