package com.paymatch.matching;

import com.paymatch.matching.rule.CombinedPaymentRule;
import com.paymatch.matching.rule.CounterpartyAmountRule;
import com.paymatch.matching.rule.InvoiceNumberRule;
import com.paymatch.transaction.BankTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real rules against a small in-memory book of invoices. Each test describes one situation
 * an accountant meets in practice.
 */
class MatchingEngineTest {

    private static final String ACME_IBAN = "PL61109010140000071219812874";
    private static final String KOWALCZYK_IBAN = "PL41194010763344556677889911";

    private final MatchingEngine engine = new MatchingEngine(
            List.of(new InvoiceNumberRule(), new CounterpartyAmountRule(), new CombinedPaymentRule()));

    private final List<InvoiceSnapshot> invoices = new ArrayList<>();
    private final List<CounterpartySnapshot> counterparties = new ArrayList<>();
    private long nextInvoiceId = 1;

    @BeforeEach
    void setUp() {
        counterparties.add(new CounterpartySnapshot(1L, "ACME Sp. z o.o.", "5260181596", ACME_IBAN));
        counterparties.add(new CounterpartySnapshot(2L, "Kowalczyk Transport", "5432319484", null));
        counterparties.add(new CounterpartySnapshot(3L, "Łukasz Żółkiewski", null, null));
    }

    // --- invoice number in the title

    @Test
    void exactNumberAndAmountIsAutoConfirmable() {
        InvoiceSnapshot invoice = invoice("FV/10/2026", 1L, "1230.50");

        List<MatchCandidate> result = suggest(tx("1230.50", "Zaplata FV/10/2026", "ACME SP Z OO", ACME_IBAN));

        assertThat(result.getFirst().rule()).isEqualTo(MatchRule.EXACT);
        assertThat(result.getFirst().score()).isEqualTo(100);
        assertThat(result.getFirst().items()).singleElement().extracting(i -> i.invoice()).isEqualTo(invoice);
        assertThat(engine.autoConfirmable(result)).isPresent();
    }

    @Test
    void numberWrittenWithSpacesAndLowercaseIsStillExact() {
        invoice("FV/4/09/2026", 1L, "615.00");

        List<MatchCandidate> result = suggest(tx("615.00", "fv 4 09 2026", null, null));

        assertThat(result.getFirst().rule()).isEqualTo(MatchRule.EXACT);
        assertThat(engine.autoConfirmable(result)).isPresent();
    }

    @Test
    void typoInTitleGoesToReviewInsteadOfAutoConfirm() {
        invoice("FV/2/10/2026", 1L, "7380.00");

        List<MatchCandidate> result = suggest(tx("7380.00", "Faktura FV/2/1O/2026", null, ACME_IBAN));

        MatchCandidate top = result.getFirst();
        assertThat(top.rule()).isEqualTo(MatchRule.EXACT);
        assertThat(top.score()).isBetween(85, 94);
        assertThat(top.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(top.reasons()).anySatisfy(reason -> assertThat(reason.message()).contains("typo"));
        assertThat(engine.autoConfirmable(result)).isEmpty();
    }

    @Test
    void numberWithoutPrefixGoesToReview() {
        invoice("FV/2/09/2026", 1L, "4920.00");

        List<MatchCandidate> result = suggest(tx("4920.00", "faktura 2/09/2026", null, null));

        assertThat(result.getFirst().rule()).isEqualTo(MatchRule.EXACT);
        assertThat(result.getFirst().score()).isEqualTo(85);
        assertThat(engine.autoConfirmable(result)).isEmpty();
    }

    @Test
    void lowerAmountIsPartialPayment() {
        invoice("FV/2/09/2026", 2L, "4920.00");

        MatchCandidate top = suggest(tx("2000.00", "Zaplata za fv 2/09/2026 czesc 1", null, null)).getFirst();

        assertThat(top.rule()).isEqualTo(MatchRule.PARTIAL_PAYMENT);
        assertThat(top.confidence()).isEqualTo(Confidence.MEDIUM);
        assertThat(top.items().getFirst().amount()).isEqualByComparingTo("2000.00");
        assertThat(top.reasons()).anySatisfy(reason -> assertThat(reason.message()).contains("2 920,00 zł less"));
    }

    @Test
    void secondPartOfPartialPaymentIsExactForTheRemainder() {
        invoice("FV/2/09/2026", 2L, "4920.00", "2000.00");

        List<MatchCandidate> result = suggest(tx("2920.00", "FV/2/09/2026 doplata", null, null));

        assertThat(result.getFirst().rule()).isEqualTo(MatchRule.EXACT);
        assertThat(engine.autoConfirmable(result)).isPresent();
    }

    @Test
    void higherAmountIsOverpayment() {
        invoice("FV/9/10/2026", 2L, "5535.00");

        MatchCandidate top = suggest(tx("5600.00", "FV/9/10/2026", null, null)).getFirst();

        assertThat(top.rule()).isEqualTo(MatchRule.OVERPAYMENT);
        assertThat(top.items().getFirst().amount()).as("the whole transfer goes to the invoice").isEqualByComparingTo("5600.00");
        assertThat(engine.autoConfirmable(List.of(top))).isEmpty();
    }

    @Test
    void paymentForAlreadyPaidInvoiceIsFlaggedAsPossibleDoublePayment() {
        invoice("FV/1/09/2026", 1L, "1230.50", "1230.50");

        MatchCandidate top = suggest(tx("1230.50", "FV/1/09/2026", null, null)).getFirst();

        assertThat(top.rule()).isEqualTo(MatchRule.ALREADY_PAID);
        assertThat(top.confidence()).isEqualTo(Confidence.LOW);
    }

    @Test
    void severalNumbersInTitleWithMatchingSum() {
        invoice("FV/5/10/2026", 2L, "3075.00");
        invoice("FV/10/10/2026", 2L, "1537.50");

        MatchCandidate top = suggest(tx("4612.50", "FV/5/10/2026, FV/10/10/2026", null, null)).getFirst();

        assertThat(top.rule()).isEqualTo(MatchRule.MULTIPLE_INVOICES);
        assertThat(top.items()).extracting(item -> item.amount().toPlainString()).containsExactly("3075.00", "1537.50");
        assertThat(engine.autoConfirmable(List.of(top))).as("combined payments are always reviewed").isEmpty();
    }

    @Test
    void senderAccountOfAnotherCounterpartyLowersScore() {
        // ACME's account, but the invoice in the title belongs to Kowalczyk
        invoice("FV/5/10/2026", 2L, "3075.00");

        MatchCandidate top = suggest(tx("3075.00", "FV/5/10/2026", "ACME", ACME_IBAN)).getFirst();

        assertThat(top.rule()).isEqualTo(MatchRule.EXACT);
        assertThat(top.score()).isEqualTo(85);
        assertThat(top.reasons()).anySatisfy(reason -> assertThat(reason.message()).contains("issued to Kowalczyk"));
    }

    @Test
    void twoExactCandidatesBlockAutoConfirm() {
        invoice("FV/1/10/2026", 1L, "100.00");
        invoice("FV/2/10/2026", 1L, "100.00");

        List<MatchCandidate> result = suggest(tx("100.00", "FV/1/10/2026 lub FV/2/10/2026", null, null));

        assertThat(result).filteredOn(c -> c.rule() == MatchRule.EXACT).hasSize(2);
        assertThat(engine.autoConfirmable(result)).isEmpty();
    }

    // --- known counterparty, no invoice number

    @Test
    void knownAccountAndUniqueAmountIsProbable() {
        invoice("FV/1/10/2026", 1L, "999.90");
        invoice("FV/2/10/2026", 1L, "120.00");

        MatchCandidate top = suggest(tx("999.90", "przelew", "ACME", ACME_IBAN)).getFirst();

        assertThat(top.rule()).isEqualTo(MatchRule.COUNTERPARTY_AMOUNT);
        assertThat(top.score()).isEqualTo(75);
        assertThat(top.reasons()).extracting(MatchReason::field).contains("account", "amount");
    }

    @Test
    void recognisesSenderByNipInTitle() {
        invoice("FV/1/10/2026", 2L, "500.00");

        MatchCandidate top = suggest(tx("500.00", "/VAT/93,50/IDC/543-231-94-84/TXT/platnosc", null, null)).getFirst();

        assertThat(top.rule()).isEqualTo(MatchRule.COUNTERPARTY_AMOUNT);
        assertThat(top.reasons().getFirst().field()).isEqualTo("nip");
    }

    @Test
    void sameAmountOnSeveralInvoicesGivesLowerScoredSuggestionsOldestFirst() {
        InvoiceSnapshot older = invoice("FV/3/09/2026", 1L, "12300.00", "0.00", LocalDate.of(2026, 10, 5));
        InvoiceSnapshot newer = invoice("FV/3/10/2026", 1L, "12300.00", "0.00", LocalDate.of(2026, 11, 5));

        List<MatchCandidate> result = suggest(tx("12300.00", "Faktura za wrzesien", null, ACME_IBAN));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).items().getFirst().invoice()).isEqualTo(older);
        assertThat(result.get(1).items().getFirst().invoice()).isEqualTo(newer);
        assertThat(result.get(0).confidence()).isEqualTo(Confidence.LOW);
    }

    @Test
    void recognisesSenderByNameIgnoringDiacriticsCaseAndLegalForm() {
        invoice("FV/5/09/2026", 3L, "369.00");
        invoice("FV/1/10/2026", 1L, "50.00");

        List<MatchCandidate> byPerson = suggest(tx("369.00", "zaplata za fakture", "ŁUKASZ ŻÓŁKIEWSKI", null));
        List<MatchCandidate> byCompany = suggest(tx("50.00", "zaplata", "ACME SPÓŁKA Z OGRANICZONĄ ODPOWIEDZIALNOŚCIĄ UL. PROSTA 1", null));

        assertThat(byPerson.getFirst().score()).isEqualTo(60);
        assertThat(byPerson.getFirst().reasons().getFirst().field()).isEqualTo("name");
        assertThat(byCompany).singleElement().extracting(MatchCandidate::rule).isEqualTo(MatchRule.COUNTERPARTY_AMOUNT);
    }

    @Test
    void unknownSenderAndNoNumberGivesNothing() {
        invoice("FV/1/10/2026", 1L, "120.00");

        assertThat(suggest(tx("120.00", "Zwrot nadplaty za usluge", "HOSTING PRO SP. Z O.O.", "PL37249000050000450012345678"))).isEmpty();
    }

    // --- combined payments

    @Test
    void amountEqualToSumOfSeveralOpenInvoicesIsCombinedPayment() {
        invoice("FV/5/10/2026", 2L, "3075.00", "0.00", LocalDate.of(2026, 10, 28));
        invoice("FV/7/10/2026", 2L, "800.00", "0.00", LocalDate.of(2026, 11, 2));
        invoice("FV/10/10/2026", 2L, "1537.50", "0.00", LocalDate.of(2026, 11, 9));

        MatchCandidate top = suggest(tx("4612.50", "Platnosc za faktury", "KOWALCZYK TRANSPORT UL. SPEDYCYJNA 8", KOWALCZYK_IBAN)).getFirst();

        assertThat(top.rule()).isEqualTo(MatchRule.COMBINED_PAYMENT);
        assertThat(top.score()).as("recognised by name only").isEqualTo(60);
        assertThat(top.items()).extracting(item -> item.invoice().getNumber()).containsExactly("FV/5/10/2026", "FV/10/10/2026");
        assertThat(top.totalAmount()).isEqualByComparingTo("4612.50");
    }

    @Test
    void ambiguousCombinationGetsLowerScore() {
        invoice("FV/1/10/2026", 1L, "100.00");
        invoice("FV/2/10/2026", 1L, "200.00");
        invoice("FV/3/10/2026", 1L, "300.00");

        MatchCandidate top = suggest(tx("300.00", "platnosc", null, ACME_IBAN)).stream()
                .filter(c -> c.rule() == MatchRule.COMBINED_PAYMENT).findFirst().orElseThrow();

        // 100 + 200 is found, and FV/3 alone also matches through the counterparty rule
        assertThat(top.score()).isEqualTo(70);
        assertThat(top.items()).hasSize(2);
    }

    @Test
    void combinedPaymentLooksAtMostAtFourInvoices() {
        for (int i = 1; i <= 5; i++) {
            invoice("FV/" + i + "/10/2026", 1L, "100.00");
        }

        List<MatchCandidate> result = suggest(tx("500.00", "platnosc", null, ACME_IBAN));

        assertThat(result).noneMatch(c -> c.rule() == MatchRule.COMBINED_PAYMENT);
    }

    @Test
    void partlyPaidInvoicesCountWithWhatIsLeft() {
        invoice("FV/1/10/2026", 1L, "1000.00", "600.00");
        invoice("FV/2/10/2026", 1L, "250.00");

        MatchCandidate top = suggest(tx("650.00", "reszta", null, ACME_IBAN)).getFirst();

        assertThat(top.rule()).isEqualTo(MatchRule.COMBINED_PAYMENT);
        assertThat(top.items()).extracting(item -> item.amount().toPlainString()).containsExactly("400.00", "250.00");
    }

    // --- general behaviour

    @Test
    void outgoingTransactionsAreNeverMatched() {
        invoice("FV/1/10/2026", 1L, "100.00");

        assertThat(suggest(tx("-100.00", "FV/1/10/2026", null, ACME_IBAN))).isEmpty();
    }

    @Test
    void invoicesInAnotherCurrencyAreIgnored() {
        invoices.add(new InvoiceSnapshot(nextInvoiceId++, "FV/1/10/2026", 1L, "ACME", "EUR",
                new BigDecimal("100.00"), new BigDecimal("0.00"), LocalDate.of(2026, 10, 15)));

        assertThat(suggest(tx("100.00", "FV/1/10/2026", null, ACME_IBAN))).isEmpty();
    }

    @Test
    void sameInvoiceFromTwoRulesKeepsTheBetterExplanation() {
        invoice("FV/1/10/2026", 1L, "100.00");

        List<MatchCandidate> result = suggest(tx("100.00", "FV/1/10/2026", null, ACME_IBAN));

        assertThat(result).singleElement().satisfies(c -> {
            assertThat(c.rule()).isEqualTo(MatchRule.EXACT);
            assertThat(c.score()).isEqualTo(100);
        });
    }

    @Test
    void rejectedSuggestionIsNotOfferedAgain() {
        InvoiceSnapshot invoice = invoice("FV/1/10/2026", 1L, "100.00");
        BankTransaction tx = tx("100.00", "FV/1/10/2026", null, null);

        List<MatchCandidate> result = engine.suggest(tx, context(), Set.of(String.valueOf(invoice.getId())));

        assertThat(result).isEmpty();
    }

    @Test
    void returnsAtMostFiveSuggestions() {
        for (int i = 1; i <= 8; i++) {
            invoice("FV/" + i + "/10/2026", 1L, "100.00", "0.00", LocalDate.of(2026, 10, i));
        }

        assertThat(suggest(tx("100.00", "przelew", null, ACME_IBAN))).hasSize(5);
    }

    // --- helpers

    private List<MatchCandidate> suggest(BankTransaction tx) {
        return engine.suggest(tx, context(), Set.of());
    }

    private MatchingContext context() {
        return new MatchingContext(invoices, counterparties);
    }

    private InvoiceSnapshot invoice(String number, Long counterpartyId, String gross) {
        return invoice(number, counterpartyId, gross, "0.00");
    }

    private InvoiceSnapshot invoice(String number, Long counterpartyId, String gross, String paid) {
        return invoice(number, counterpartyId, gross, paid, LocalDate.of(2026, 10, 15));
    }

    private InvoiceSnapshot invoice(String number, Long counterpartyId, String gross, String paid, LocalDate dueDate) {
        String name = counterparties.stream().filter(c -> c.id().equals(counterpartyId)).findFirst().orElseThrow().name();
        InvoiceSnapshot invoice = new InvoiceSnapshot(nextInvoiceId++, number, counterpartyId, name, "PLN",
                new BigDecimal(gross), new BigDecimal(paid), dueDate);
        invoices.add(invoice);
        return invoice;
    }

    private static BankTransaction tx(String amount, String title, String name, String account) {
        return new BankTransaction(null, null, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 20),
                new BigDecimal(amount), "PLN", name, account, title, null, "key", null);
    }
}
