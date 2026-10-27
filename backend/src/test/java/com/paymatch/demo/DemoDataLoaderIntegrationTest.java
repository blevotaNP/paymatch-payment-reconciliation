package com.paymatch.demo;

import com.paymatch.IntegrationTest;
import com.paymatch.audit.AuditAction;
import com.paymatch.audit.AuditEventRepository;
import com.paymatch.invoice.InvoiceRepository;
import com.paymatch.invoice.InvoiceStatus;
import com.paymatch.statement.StatementImportRepository;
import com.paymatch.transaction.BankTransactionRepository;
import com.paymatch.transaction.MatchStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** Starts its own application context with demo data switched on and checks the result of the first start. */
@IntegrationTest
@TestPropertySource(properties = {"app.demo-data.enabled=true", "app.demo-data.directory=../samples"})
class DemoDataLoaderIntegrationTest {

    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private StatementImportRepository importRepository;
    @Autowired
    private BankTransactionRepository transactionRepository;
    @Autowired
    private AuditEventRepository auditRepository;

    @Test
    void firstStartLoadsARealisticMix() {
        assertThat(invoiceRepository.count()).isEqualTo(15);
        assertThat(importRepository.count()).isEqualTo(5);

        assertThat(transactionRepository.countByMatchStatus(MatchStatus.MATCHED)).as("6 automatic + 2 confirmed").isEqualTo(8);
        assertThat(transactionRepository.countByMatchStatus(MatchStatus.SUGGESTED)).isEqualTo(6);
        assertThat(transactionRepository.countByMatchStatus(MatchStatus.IGNORED)).isEqualTo(1);
        assertThat(invoiceRepository.findAll()).filteredOn(i -> i.getNumber().equals("FV/2/09/2026"))
                .singleElement().extracting(i -> i.getStatus()).isEqualTo(InvoiceStatus.PAID);

        assertThat(auditRepository.findAll()).extracting(e -> e.getAction())
                .contains(AuditAction.INVOICES_IMPORTED, AuditAction.IMPORT_CREATED, AuditAction.MATCH_AUTO_CONFIRMED,
                        AuditAction.MATCH_CONFIRMED, AuditAction.TRANSACTION_IGNORED);
    }

    @Test
    void datesAreMovedToTheCurrentMonth() {
        YearMonth thisMonth = YearMonth.now(ZoneId.of("Europe/Warsaw"));
        LocalDate latestPayment = transactionRepository.findAll().stream()
                .map(tx -> tx.getBookingDate()).max(LocalDate::compareTo).orElseThrow();

        assertThat(YearMonth.from(latestPayment)).isEqualTo(thisMonth);
    }
}
