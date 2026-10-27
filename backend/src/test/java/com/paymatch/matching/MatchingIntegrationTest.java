package com.paymatch.matching;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymatch.DatabaseCleaner;
import com.paymatch.IntegrationTest;
import com.paymatch.invoice.InvoiceRepository;
import com.paymatch.invoice.InvoiceStatus;
import com.paymatch.statement.TestFiles;
import com.paymatch.transaction.BankTransaction;
import com.paymatch.transaction.BankTransactionRepository;
import com.paymatch.transaction.MatchStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Imports the demo invoices and all sample statements, then checks what the engine did with each payment. */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "anna")
class MatchingIntegrationTest {

    private static final List<String> STATEMENTS = List.of(
            "mt940_pekao_2026-09.sta",
            "mt940_pekao_2026-09-25_2026-10-10.sta",
            "pko_bp_historia_2026-10.csv",
            "mbank_lista_operacji_2026-10.csv",
            "ing_lista_transakcji_2026-10.csv");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DatabaseCleaner databaseCleaner;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private BankTransactionRepository transactionRepository;
    @Autowired
    private MatchSuggestionRepository suggestionRepository;

    @BeforeEach
    void cleanDatabase() {
        databaseCleaner.clean();
    }

    @Test
    void autoConfirmsPerfectMatchesAndSendsTheRestToReview() throws Exception {
        importInvoices();
        for (String statement : STATEMENTS) {
            importStatement(statement);
        }

        // perfect number + exact amount: paid without anyone looking
        assertThat(invoiceStatus("FV/1/09/2026")).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoiceStatus("FV/4/09/2026")).as("written as 'FV 4 09 2026'").isEqualTo(InvoiceStatus.PAID);
        assertThat(invoiceStatus("FV/1/10/2026")).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoiceStatus("FV/4/10/2026")).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoiceStatus("FV/6/10/2026")).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoiceStatus("FV/8/10/2026")).isEqualTo(InvoiceStatus.PAID);

        // everything else waits for a person
        assertThat(topSuggestion("Zaplata za fv 2/09/2026 czesc 1")).isEqualTo(MatchRule.PARTIAL_PAYMENT);
        assertThat(topSuggestion("FV/2/09/2026 doplata")).isEqualTo(MatchRule.PARTIAL_PAYMENT);
        assertThat(topSuggestion("Faktura FV/2/1O/2026")).isEqualTo(MatchRule.EXACT);
        assertThat(topSuggestion("Faktura za wrzesien")).isEqualTo(MatchRule.COUNTERPARTY_AMOUNT);
        assertThat(topSuggestion("zaplata za fakture")).isEqualTo(MatchRule.COUNTERPARTY_AMOUNT);
        assertThat(topSuggestion("Platnosc za faktury")).isEqualTo(MatchRule.COMBINED_PAYMENT);
        assertThat(topSuggestion("FV/9/10/2026")).isEqualTo(MatchRule.OVERPAYMENT);
        assertThat(topSuggestion("FV/7/10/2026 rata 1")).isEqualTo(MatchRule.PARTIAL_PAYMENT);
        assertThat(statusOf("Zwrot nadplaty za usluge hostingowa")).isEqualTo(MatchStatus.UNMATCHED);

        assertThat(invoiceStatus("FV/2/09/2026")).as("partial payments are not confirmed yet").isEqualTo(InvoiceStatus.UNPAID);
        assertThat(transactionRepository.findAll()).filteredOn(tx -> !tx.isIncoming())
                .allMatch(tx -> tx.getMatchStatus() == MatchStatus.UNMATCHED);
    }

    @Test
    void importResponseSummarisesMatching() throws Exception {
        importInvoices();

        JsonNode response = importStatement("mbank_lista_operacji_2026-10.csv");

        assertThat(response.at("/matching/checked").asInt()).isEqualTo(4);
        assertThat(response.at("/matching/autoConfirmed").asInt()).isEqualTo(1);
        assertThat(response.at("/matching/suggested").asInt()).isEqualTo(2);
        assertThat(response.at("/matching/unmatched").asInt()).isEqualTo(1);
    }

    @Test
    void invoicesImportedAfterStatementsAreStillMatched() throws Exception {
        importStatement("mt940_pekao_2026-09.sta");
        assertThat(transactionRepository.findAll()).filteredOn(BankTransaction::isIncoming)
                .allMatch(tx -> tx.getMatchStatus() == MatchStatus.UNMATCHED);

        JsonNode result = importInvoices();

        assertThat(result.at("/matching/autoConfirmed").asInt()).isEqualTo(2);
        assertThat(invoiceStatus("FV/1/09/2026")).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void reimportingAStatementDoesNotPayAnInvoiceTwice() throws Exception {
        importInvoices();
        importStatement("mt940_pekao_2026-09.sta");
        importStatement("mt940_pekao_2026-09.sta");

        assertThat(invoiceRepository.findAll()).filteredOn(i -> i.getNumber().equals("FV/1/09/2026"))
                .singleElement().satisfies(i -> assertThat(i.getPaidAmount()).isEqualByComparingTo("1230.50"));
    }

    @Test
    void undoingAnImportRevertsItsAutomaticMatches() throws Exception {
        importInvoices();
        long importId = importStatement("mbank_lista_operacji_2026-10.csv").get("id").asLong();
        assertThat(invoiceStatus("FV/4/10/2026")).isEqualTo(InvoiceStatus.PAID);

        mockMvc.perform(post("/api/imports/{id}/undo", importId).with(csrf())).andExpect(status().isOk());

        assertThat(invoiceStatus("FV/4/10/2026")).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(invoiceRepository.findAll()).allMatch(i -> i.getPaidAmount().compareTo(BigDecimal.ZERO) == 0);
        assertThat(suggestionRepository.count()).isZero();
    }

    @Test
    void transactionDetailsExplainTheSuggestion() throws Exception {
        importInvoices();
        importStatement("pko_bp_historia_2026-10.csv");
        Long id = transactionId("Faktura FV/2/1O/2026");

        mockMvc.perform(get("/api/transactions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.matchStatus").value("SUGGESTED"))
                .andExpect(jsonPath("$.suggestions[0].rule").value("EXACT"))
                .andExpect(jsonPath("$.suggestions[0].confidence").value("HIGH"))
                .andExpect(jsonPath("$.suggestions[0].items[0].invoiceNumber").value("FV/2/10/2026"))
                .andExpect(jsonPath("$.suggestions[0].reasons[0].field").value("title"))
                .andExpect(jsonPath("$.suggestions[0].reasons[2].field").value("account"));

        mockMvc.perform(get("/api/transactions").param("matchStatus", "SUGGESTED"))
                .andExpect(jsonPath("$.content[0].bestSuggestion.ruleLabel").exists());
    }

    @Test
    void deletingAnInvoiceRemovesItsPendingSuggestions() throws Exception {
        importInvoices();
        importStatement("mbank_lista_operacji_2026-10.csv");
        Long pixelInvoice = invoiceId("FV/9/10/2026");

        mockMvc.perform(delete("/api/invoices/{id}", pixelInvoice).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(statusOf("FV/9/10/2026")).isEqualTo(MatchStatus.UNMATCHED);
    }

    private JsonNode importInvoices() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "invoices.csv", "text/csv", TestFiles.bytes("samples/invoices.csv"));
        String body = mockMvc.perform(multipart("/api/invoices/import").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode importStatement(String name) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, "application/octet-stream",
                TestFiles.bytes("samples/statements/" + name));
        String body = mockMvc.perform(multipart("/api/imports").file(file).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private InvoiceStatus invoiceStatus(String number) {
        return invoiceRepository.findAll().stream().filter(i -> i.getNumber().equals(number)).findFirst().orElseThrow().getStatus();
    }

    private Long invoiceId(String number) {
        return invoiceRepository.findAll().stream().filter(i -> i.getNumber().equals(number)).findFirst().orElseThrow().getId();
    }

    private Long transactionId(String title) {
        return transactionRepository.findAll().stream().filter(t -> title.equals(t.getTitle())).findFirst().orElseThrow().getId();
    }

    private MatchStatus statusOf(String title) {
        return transactionRepository.findById(transactionId(title)).orElseThrow().getMatchStatus();
    }

    private MatchRule topSuggestion(String title) {
        Long id = transactionId(title);
        assertThat(statusOf(title)).as(title).isEqualTo(MatchStatus.SUGGESTED);
        return suggestionRepository.findByTransactionIdOrderByScoreDesc(id).getFirst().getRule();
    }
}
