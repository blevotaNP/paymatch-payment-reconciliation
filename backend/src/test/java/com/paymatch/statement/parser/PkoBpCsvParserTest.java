package com.paymatch.statement.parser;

import com.paymatch.statement.TestFiles;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PkoBpCsvParserTest {

    private final PkoBpCsvParser parser = new PkoBpCsvParser();

    @Test
    void parsesKeyValueDescriptionColumns() {
        ParsedStatement statement = parser.parse(TestFiles.sample("pko_bp_historia_2026-10.csv"));

        assertThat(statement.getTransactions()).hasSize(7);
        assertThat(statement.getSkipped()).isEmpty();
        assertThat(statement.getAccountIban()).as("PKO export has no own account").isNull();
        assertThat(statement.getPeriodFrom()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(statement.getPeriodTo()).isEqualTo(LocalDate.of(2026, 10, 24));

        ParsedTransaction first = statement.getTransactions().getFirst();
        assertThat(first.amount()).isEqualTo(new BigDecimal("1845.00"));
        assertThat(first.title()).isEqualTo("FV/1/10/2026");
        assertThat(first.counterpartyName()).isEqualTo("BIURO RACHUNKOWE BILANS");
        assertThat(first.counterpartyAccount()).isEqualTo("PL65102028920000556677889900");
        assertThat(first.bankReference()).isNull();
        assertThat(first.lineNumber()).isEqualTo(2);
    }

    @Test
    void keepsPolishCharactersFromWindows1250() {
        ParsedStatement statement = parser.parse(TestFiles.sample("pko_bp_historia_2026-10.csv"));

        assertThat(statement.getTransactions().get(2).counterpartyName()).isEqualTo("ŁUKASZ ŻÓŁKIEWSKI");
    }

    @Test
    void usesFreeTextWhenThereIsNoTitleKey() {
        ParsedStatement statement = parser.parse(TestFiles.sample("pko_bp_historia_2026-10.csv"));

        ParsedTransaction fee = statement.getTransactions().get(3);
        assertThat(fee.amount()).isEqualTo(new BigDecimal("-15.00"));
        assertThat(fee.title()).isEqualTo("Opłata za przelew natychmiastowy");
    }

    @Test
    void takesMerchantNameFromCardPaymentLocation() {
        ParsedStatement statement = parser.parse(TestFiles.sample("pko_bp_historia_2026-10.csv"));

        ParsedTransaction card = statement.getTransactions().getLast();
        assertThat(card.amount()).isEqualTo(new BigDecimal("-86.97"));
        assertThat(card.counterpartyName()).isEqualTo("ORLEN STACJA NR 123");
    }

    @Test
    void failsWhenAmountColumnIsMissing() {
        assertThatThrownBy(() -> parser.parse(TestFiles.broken("pko_missing_amount_column.csv")))
                .isInstanceOf(StatementParseException.class)
                .hasMessageContaining("Kwota");
    }
}
