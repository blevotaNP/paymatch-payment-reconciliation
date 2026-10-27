package com.paymatch.matching;

import com.paymatch.matching.InvoiceNumberMatcher.NumberMatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceNumberMatcherTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "FV/10/2026",
            "fv/10/2026",
            "FV 10/2026",
            "FV 10 2026",
            "FV-10-2026",
            "FV10/2026",
            "FV/010/2026",
            "Zapłata za fakturę nr FV/10/2026, dziękujemy",
            "przelew FV.10.2026"
    })
    void findsNumberWrittenInDifferentWays(String title) {
        Optional<NumberMatch> match = find("FV/10/2026", title);

        assertThat(match).isPresent();
        assertThat(match.get().isPerfect()).isTrue();
    }

    @Test
    void reportsTheTextAsThePayerWroteIt() {
        assertThat(find("FV/4/09/2026", "Platnosc FV 4 09 2026 dzieki")).get()
                .extracting(NumberMatch::foundText).isEqualTo("FV 4 09 2026");
    }

    @Test
    void fixesLetterOInsteadOfZero() {
        Optional<NumberMatch> match = find("FV/2/10/2026", "Faktura FV/2/1O/2026");

        assertThat(match).isPresent();
        assertThat(match.get().corrected()).isTrue();
        assertThat(match.get().isPerfect()).isFalse();
    }

    @Test
    void acceptsNumberWithoutPrefixButMarksIt() {
        Optional<NumberMatch> match = find("FV/2/09/2026", "zaplata za fakture 2/09/2026");

        assertThat(match).isPresent();
        assertThat(match.get().withoutPrefix()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "FV/11/10/2026",          // different number that contains ours as text
            "FV/1/10/20261",          // year typo makes it a different number
            "FV/21/10/2026",
            "FV/1/11/2026",
            "FV/1/10/2026/2"          // glued to one more number part
    })
    void doesNotMatchDifferentNumbers(String title) {
        assertThat(find("FV/1/10/2026", title)).isEmpty();
    }

    @Test
    void doesNotMatchInsideALongerNumber() {
        // "10/2026" is a valid invoice of another series, but here it is the tail of FV/1/10/2026
        assertThat(find("10/2026", "FV/1/10/2026")).isEmpty();
        assertThat(find("FV/1/10", "FV/1/10/2026")).isEmpty();
    }

    @Test
    void numericPartAfterAnotherSeriesPrefixBelongsToThatSeries() {
        InvoiceSnapshot invoice = invoice("FV/2/09/2026");
        String title = "Korekta KOR/2/09/2026";

        assertThat(InvoiceNumberMatcher.find(invoice, InvoiceNumberTokens.tokenize(title), title, Set.of("FV", "KOR"))).isEmpty();
    }

    @Test
    void needsAtLeastTwoNumericPartsWithoutPrefix() {
        assertThat(find("FV/2026", "zaplata 2026")).isEmpty();
    }

    private static Optional<NumberMatch> find(String number, String title) {
        return InvoiceNumberMatcher.find(invoice(number), InvoiceNumberTokens.tokenize(title), title, Set.of("FV"));
    }

    private static InvoiceSnapshot invoice(String number) {
        return new InvoiceSnapshot(1L, number, 1L, "ACME", "PLN", new BigDecimal("100.00"), new BigDecimal("0.00"),
                LocalDate.of(2026, 10, 15));
    }
}
