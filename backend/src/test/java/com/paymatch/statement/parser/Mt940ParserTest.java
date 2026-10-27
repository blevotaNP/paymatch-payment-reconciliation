package com.paymatch.statement.parser;

import com.paymatch.statement.TestFiles;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Mt940ParserTest {

    private final Mt940Parser parser = new Mt940Parser();

    @Test
    void parsesPolishMt940WithStructuredDetails() {
        ParsedStatement statement = parser.parse(TestFiles.sample("mt940_pekao_2026-09.sta"));

        assertThat(statement.getAccountIban()).isEqualTo("PL04124010371111001122334455");
        assertThat(statement.getPeriodFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(statement.getPeriodTo()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(statement.getTransactions()).hasSize(5);
        assertThat(statement.getSkipped()).isEmpty();
        assertThat(statement.getWarnings()).as("opening + entries = closing").isEmpty();

        ParsedTransaction first = statement.getTransactions().getFirst();
        assertThat(first.bookingDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(first.amount()).isEqualTo(new BigDecimal("1230.50"));
        assertThat(first.currency()).isEqualTo("PLN");
        assertThat(first.title()).isEqualTo("FV/1/09/2026");
        // ~32 and ~33 are joined, and the line break inside ~31/~38 does not split the account
        assertThat(first.counterpartyName()).isEqualTo("ACME SP. Z O.O. UL. PROSTA 1 00-001 WARSZAWA");
        assertThat(first.counterpartyAccount()).isEqualTo("PL61109010140000071219812874");
        assertThat(first.bankReference()).isEqualTo("2609140000004711");
    }

    @Test
    void readsDebitsAsNegativeAndJoinsWrappedTitle() {
        ParsedStatement statement = parser.parse(TestFiles.sample("mt940_pekao_2026-09.sta"));

        ParsedTransaction fee = statement.getTransactions().get(2);
        assertThat(fee.amount()).isEqualTo(new BigDecimal("-45.00"));
        assertThat(fee.title()).isEqualTo("Opłata za prowadzenie rachunku 09/2026");
        assertThat(fee.counterpartyName()).isNull();
    }

    @Test
    void skipsUnreadableEntryAndWarnsAboutMissingClosingBalance() {
        ParsedStatement statement = parser.parse(TestFiles.broken("mt940_truncated.sta"));

        assertThat(statement.getTransactions()).extracting(ParsedTransaction::amount)
                .containsExactly(new BigDecimal("250.00"), new BigDecimal("-99.99"));
        assertThat(statement.getSkipped()).singleElement().satisfies(row -> {
            assertThat(row.line()).isEqualTo(7);
            assertThat(row.reason()).isEqualTo("Unrecognized :61: entry");
        });
        assertThat(statement.getWarnings()).singleElement().asString().contains("no closing balance");
    }

    @Test
    void warnsWhenBalancesDoNotAddUp() {
        ParsedStatement statement = parser.parse(TestFiles.broken("mt940_balance_mismatch.sta"));

        assertThat(statement.getTransactions()).hasSize(1);
        assertThat(statement.getWarnings()).singleElement().asString().startsWith("Balance mismatch");
    }

    @Test
    void parsesUnstructuredDetailsAsTitleAndHandlesSwiftEnvelope() {
        String content = """
                {1:F01BANKPLPWAXXX0000000000}{2:I940BANKPLPWXXXXN}{4:
                :20:REF1
                :25:PL61109010140000071219812874
                :60F:C261001PLN0,00
                :61:261002C1000,00NTRFNONREF
                :86:Payment for FV/5/10/2026 thank you
                :62F:C261002PLN1000,00
                -}""";

        ParsedStatement statement = parser.parse(content);

        ParsedTransaction tx = statement.getTransactions().getFirst();
        assertThat(tx.title()).isEqualTo("Payment for FV/5/10/2026 thank you");
        assertThat(tx.bookingDate()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(tx.bankReference()).as("NONREF is not a reference").isNull();
        assertThat(statement.getWarnings()).isEmpty();
    }

    @Test
    void entryDateInJanuaryForDecemberValueDateBelongsToNextYear() {
        String content = """
                :20:REF1
                :25:PL61109010140000071219812874
                :60F:C261231PLN0,00
                :61:2612310102C50,00NTRFNONREF//X1
                :62F:C270102PLN50,00
                """;

        ParsedTransaction tx = parser.parse(content).getTransactions().getFirst();

        assertThat(tx.valueDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(tx.bookingDate()).isEqualTo(LocalDate.of(2027, 1, 2));
    }

    @Test
    void rejectsFileWithoutMt940Tags() {
        assertThatThrownBy(() -> parser.parse(TestFiles.broken("random_text.txt")))
                .isInstanceOf(StatementParseException.class);
    }
}
