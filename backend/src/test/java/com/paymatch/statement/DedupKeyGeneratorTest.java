package com.paymatch.statement;

import com.paymatch.statement.parser.ParsedTransaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DedupKeyGeneratorTest {

    private static final String ACCOUNT = "PL46114020040000310212345678";

    @Test
    void sameTransactionInTwoFilesGetsTheSameKey() {
        ParsedTransaction tx = tx("2026-10-23", "2460.00", "FV/4/10/2026", null);

        List<String> first = DedupKeyGenerator.generate(BankFormat.MBANK_CSV, ACCOUNT, List.of(tx));
        List<String> second = DedupKeyGenerator.generate(BankFormat.MBANK_CSV, ACCOUNT,
                List.of(tx("2026-10-22", "100.00", "other", null), tx));

        assertThat(second.get(1)).isEqualTo(first.getFirst());
    }

    @Test
    void identicalTransactionsOnTheSameDayStayDistinct() {
        ParsedTransaction fee = tx("2026-10-21", "-15.00", "Opłata za przelew natychmiastowy", null);

        List<String> keys = DedupKeyGenerator.generate(BankFormat.PKO_BP_CSV, null, List.of(fee, fee));

        assertThat(keys.get(0)).isNotEqualTo(keys.get(1));
    }

    @Test
    void overlappingExportWithOnlyOneOfTheTwinsMatchesTheFirstOne() {
        ParsedTransaction fee = tx("2026-10-21", "-15.00", "Opłata za przelew natychmiastowy", null);

        List<String> both = DedupKeyGenerator.generate(BankFormat.PKO_BP_CSV, null, List.of(fee, fee));
        List<String> one = DedupKeyGenerator.generate(BankFormat.PKO_BP_CSV, null, List.of(fee));

        assertThat(one.getFirst()).isEqualTo(both.getFirst());
    }

    @Test
    void titleFormattingDifferencesDoNotMatter() {
        List<String> a = DedupKeyGenerator.generate(BankFormat.MBANK_CSV, ACCOUNT, List.of(tx("2026-10-23", "50.00", "Opłata  za kartę", null)));
        List<String> b = DedupKeyGenerator.generate(BankFormat.MBANK_CSV, ACCOUNT, List.of(tx("2026-10-23", "50.00", "OPLATA ZA KARTE", null)));

        assertThat(a).isEqualTo(b);
    }

    @Test
    void differentAmountGivesDifferentKey() {
        List<String> a = DedupKeyGenerator.generate(BankFormat.MBANK_CSV, ACCOUNT, List.of(tx("2026-10-23", "50.00", "x", null)));
        List<String> b = DedupKeyGenerator.generate(BankFormat.MBANK_CSV, ACCOUNT, List.of(tx("2026-10-23", "50.01", "x", null)));

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void bankReferenceWinsOverContent() {
        List<String> a = DedupKeyGenerator.generate(BankFormat.ING_CSV, ACCOUNT, List.of(tx("2026-10-23", "50.00", "first title", "REF-1")));
        List<String> b = DedupKeyGenerator.generate(BankFormat.ING_CSV, ACCOUNT, List.of(tx("2026-10-24", "50.00", "changed title", "REF-1")));
        List<String> otherAccount = DedupKeyGenerator.generate(BankFormat.ING_CSV, "PL50105014451000009012345678",
                List.of(tx("2026-10-23", "50.00", "first title", "REF-1")));

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(otherAccount);
    }

    private static ParsedTransaction tx(String date, String amount, String title, String reference) {
        return new ParsedTransaction(1, LocalDate.parse(date), LocalDate.parse(date), new BigDecimal(amount), "PLN",
                "ACME", "PL61109010140000071219812874", title, reference, null);
    }
}
