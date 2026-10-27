package com.paymatch.statement.parser;

import com.paymatch.statement.TestFiles;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class MbankCsvParserTest {

    private final MbankCsvParser parser = new MbankCsvParser();

    @Test
    void readsMetadataBlockAndIgnoresSummaryRows() {
        ParsedStatement statement = parser.parse(TestFiles.sample("mbank_lista_operacji_2026-10.csv"));

        assertThat(statement.getAccountIban()).isEqualTo("PL46114020040000310212345678");
        assertThat(statement.getPeriodFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(statement.getPeriodTo()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(statement.getTransactions()).hasSize(6);
        assertThat(statement.getSkipped()).as("#Saldo końcowe is not a transaction").isEmpty();
    }

    @Test
    void parsesPolishAmountsAndQuotedAccounts() {
        ParsedStatement statement = parser.parse(TestFiles.sample("mbank_lista_operacji_2026-10.csv"));

        ParsedTransaction combined = statement.getTransactions().stream()
                .filter(tx -> tx.title().equals("Platnosc za faktury"))
                .findFirst().orElseThrow();
        assertThat(combined.amount()).isEqualTo(new BigDecimal("4612.50"));
        assertThat(combined.counterpartyAccount()).isEqualTo("PL41194010763344556677889911");
        assertThat(combined.counterpartyName()).isEqualTo("KOWALCZYK TRANSPORT UL. SPEDYCYJNA 8 ŁÓDŹ");
        assertThat(combined.lineNumber()).isEqualTo(18);
    }

    @Test
    void reportsBadRowsWithLineAndReason() {
        ParsedStatement statement = parser.parse(TestFiles.broken("mbank_bad_rows.csv"));

        assertThat(statement.getTransactions()).hasSize(1);
        assertThat(statement.getSkipped()).extracting(SkippedRow::line, SkippedRow::reason).containsExactly(
                tuple(8L, "Not a valid date: 2026-11-31"),
                tuple(9L, "Not a valid amount: 12,3,4"),
                tuple(10L, "Amount has more than 2 decimal places: 10.005"),
                tuple(11L, "Zero amount"));
    }
}
