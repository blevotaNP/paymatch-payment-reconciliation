package com.paymatch.statement;

import com.paymatch.statement.parser.IngCsvParser;
import com.paymatch.statement.parser.MbankCsvParser;
import com.paymatch.statement.parser.Mt940Parser;
import com.paymatch.statement.parser.PkoBpCsvParser;
import com.paymatch.statement.parser.StatementParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatementParserRegistryTest {

    private final StatementParserRegistry registry = new StatementParserRegistry(
            List.of(new Mt940Parser(), new PkoBpCsvParser(), new MbankCsvParser(), new IngCsvParser()));

    @ParameterizedTest
    @CsvSource({
            "mt940_pekao_2026-09.sta, MT940",
            "mt940_pekao_2026-09-25_2026-10-10.sta, MT940",
            "pko_bp_historia_2026-10.csv, PKO_BP_CSV",
            "mbank_lista_operacji_2026-10.csv, MBANK_CSV",
            "ing_lista_transakcji_2026-10.csv, ING_CSV"
    })
    void detectsFormatOfEverySample(String file, BankFormat expected) {
        assertThat(registry.detect(TestFiles.sample(file))).map(StatementParser::format).hasValue(expected);
    }

    @Test
    void detectsNothingForUnrelatedFile() {
        assertThat(registry.detect(TestFiles.broken("random_text.txt"))).isEmpty();
    }

    @Test
    void doesNotTakeInvoiceCsvForAStatement() {
        assertThat(registry.detect(TestFiles.text("samples/invoices.csv"))).isEmpty();
    }
}
