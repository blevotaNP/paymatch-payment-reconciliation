package com.paymatch.statement.parser;

import com.paymatch.statement.TestFiles;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class IngCsvParserTest {

    private final IngCsvParser parser = new IngCsvParser();

    @Test
    void parsesUtf8WithBomAndUsesTransactionNumberAsReference() {
        ParsedStatement statement = parser.parse(TestFiles.sample("ing_lista_transakcji_2026-10.csv"));

        assertThat(statement.getAccountIban()).isEqualTo("PL50105014451000009012345678");
        assertThat(statement.getPeriodFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(statement.getPeriodTo()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(statement.getTransactions()).hasSize(4);

        ParsedTransaction first = statement.getTransactions().getFirst();
        assertThat(first.amount()).isEqualTo(new BigDecimal("984.00"));
        assertThat(first.title()).isEqualTo("FV/6/10/2026");
        assertThat(first.bankReference()).isEqualTo("202610210004521337");
        assertThat(first.counterpartyAccount()).isEqualTo("PL88102010260000123400567890");
    }

    @Test
    void usesBookingDateWhenItDiffersFromTransactionDate() {
        ParsedTransaction card = parser.parse(TestFiles.sample("ing_lista_transakcji_2026-10.csv")).getTransactions().get(1);

        assertThat(card.valueDate()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(card.bookingDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(card.amount()).isEqualTo(new BigDecimal("-250.00"));
    }

    @Test
    void skipsCardAuthorizationsThatAreNotBookedAndIgnoresFooter() {
        ParsedStatement statement = parser.parse(TestFiles.sample("ing_lista_transakcji_2026-10.csv"));

        assertThat(statement.getSkipped()).singleElement().satisfies(row -> {
            assertThat(row.line()).isEqualTo(16);
            assertThat(row.reason()).contains("not booked yet");
        });
    }
}
