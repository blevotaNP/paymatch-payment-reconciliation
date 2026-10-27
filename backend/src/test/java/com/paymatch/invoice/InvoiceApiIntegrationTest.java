package com.paymatch.invoice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymatch.DatabaseCleaner;
import com.paymatch.IntegrationTest;
import com.paymatch.common.parsing.TextDecoder;
import com.paymatch.counterparty.CounterpartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "anna")
class InvoiceApiIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Warsaw"));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CounterpartyRepository counterpartyRepository;
    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void cleanDatabase() {
        databaseCleaner.clean();
    }

    @Test
    void createsInvoiceAndReturnsAmountsAsStrings() throws Exception {
        long counterpartyId = createCounterparty("ACME Sp. z o.o.", "526-018-15-96");

        mockMvc.perform(post("/api/invoices").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson("FV/10/2026", counterpartyId, TODAY.minusDays(10), TODAY.plusDays(4), "1230.5")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("FV/10/2026"))
                .andExpect(jsonPath("$.grossAmount").value("1230.50"))
                .andExpect(jsonPath("$.paidAmount").value("0.00"))
                .andExpect(jsonPath("$.remainingAmount").value("1230.50"))
                .andExpect(jsonPath("$.status").value("UNPAID"))
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.counterparty.nip").value("5260181596"))
                .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    void rejectsInvalidInvoiceWithFieldErrors() throws Exception {
        long counterpartyId = createCounterparty("ACME Sp. z o.o.", null);

        mockMvc.perform(post("/api/invoices").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson("", counterpartyId, TODAY, TODAY.minusDays(1), "10.001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("number")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("grossAmount")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("dueDateValid")));
    }

    @Test
    void rejectsDuplicateInvoiceNumberIgnoringCase() throws Exception {
        long counterpartyId = createCounterparty("ACME Sp. z o.o.", null);
        createInvoice("FV/10/2026", counterpartyId, "100.00");

        mockMvc.perform(post("/api/invoices").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson("fv/10/2026", counterpartyId, TODAY, TODAY, "100.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Invoice fv/10/2026 already exists"));
    }

    @Test
    void rejectsCounterpartyWithInvalidNip() throws Exception {
        mockMvc.perform(post("/api/counterparties").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bad NIP", "nip": "1234567890"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nip"))
                .andExpect(jsonPath("$.errors[0].message").value("invalid NIP"));
    }

    @Test
    void filtersOverdueInvoicesAndSearchesByCounterparty() throws Exception {
        long acme = createCounterparty("ACME Sp. z o.o.", null);
        long kowalski = createCounterparty("Kowalski i Syn", null);
        mockMvc.perform(post("/api/invoices").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(invoiceJson("FV/1/2026", acme, TODAY.minusDays(40), TODAY.minusDays(26), "500.00")));
        mockMvc.perform(post("/api/invoices").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(invoiceJson("FV/2/2026", kowalski, TODAY, TODAY.plusDays(14), "700.00")));

        mockMvc.perform(get("/api/invoices").param("overdue", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].number").value("FV/1/2026"))
                .andExpect(jsonPath("$.content[0].daysOverdue").value(26));

        mockMvc.perform(get("/api/invoices").param("search", "kowal"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].number").value("FV/2/2026"));
    }

    @Test
    void updatesAndDeletesInvoice() throws Exception {
        long counterpartyId = createCounterparty("ACME Sp. z o.o.", null);
        long id = createInvoice("FV/3/2026", counterpartyId, "100.00");

        mockMvc.perform(put("/api/invoices/{id}", id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson("FV/3/2026", counterpartyId, TODAY, TODAY.plusDays(7), "150.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossAmount").value("150.00"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(delete("/api/invoices/{id}", id).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/invoices/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void importsWindows1250CsvAndReportsSkippedRows() throws Exception {
        createCounterparty("Istniejący Klient", "6281948218");
        String csv = """
                number;counterparty_name;nip;iban;issue_date;due_date;gross_amount;currency
                FV/20/2026;Zakład Usług Łączności;526-018-15-96;PL61 1090 1014 0000 0712 1981 2874;01.10.2026;15.10.2026;1 230,50;PLN
                FV/21/2026;Istniejący Klient;6281948218;;2026-10-02;2026-10-16;99,99;
                FV/22/2026;Zły NIP;1234567890;;2026-10-02;2026-10-16;10,00;PLN
                FV/23/2026;Brak kwoty;;;2026-10-02;2026-10-16;;PLN
                fv/20/2026;Duplikat w pliku;;;2026-10-02;2026-10-16;10,00;PLN
                FV/24/2026;Zła data;;;2026-10-40;2026-10-16;10,00;PLN
                """;
        MockMultipartFile file = new MockMultipartFile("file", "invoices.csv", "text/csv",
                csv.getBytes(TextDecoder.WINDOWS_1250));

        String body = mockMvc.perform(multipart("/api/invoices/import").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(6))
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.detectedEncoding").value("windows-1250"))
                .andReturn().getResponse().getContentAsString();

        JsonNode skipped = objectMapper.readTree(body).get("skipped");
        assertThat(skipped).hasSize(4);
        assertThat(skipped.get(0).get("line").asInt()).isEqualTo(4);
        assertThat(skipped.get(0).get("reason").asText()).startsWith("Invalid NIP");
        assertThat(skipped.get(1).get("reason").asText()).isEqualTo("Missing gross_amount");
        assertThat(skipped.get(2).get("reason").asText()).isEqualTo("Duplicate invoice number in file");
        assertThat(skipped.get(3).get("reason").asText()).startsWith("Not a valid date");

        assertThat(counterpartyRepository.findByNip("5260181596")).get()
                .satisfies(c -> {
                    assertThat(c.getName()).isEqualTo("Zakład Usług Łączności");
                    assertThat(c.getIban()).isEqualTo("PL61109010140000071219812874");
                });
        // the existing counterparty is reused, not duplicated
        assertThat(counterpartyRepository.count()).isEqualTo(2);
    }

    @Test
    void rejectsCsvWithoutRequiredColumns() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "x.csv", "text/csv",
                "number;amount\nFV/1;10".getBytes());

        mockMvc.perform(multipart("/api/invoices/import").file(file).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Missing required columns")));
    }

    private long createCounterparty(String name, String nip) throws Exception {
        String json = objectMapper.createObjectNode().put("name", name).put("nip", nip).toString();
        String body = mockMvc.perform(post("/api/counterparties").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long createInvoice(String number, long counterpartyId, String amount) throws Exception {
        String body = mockMvc.perform(post("/api/invoices").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson(number, counterpartyId, TODAY, TODAY.plusDays(14), amount)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private String invoiceJson(String number, long counterpartyId, LocalDate issueDate, LocalDate dueDate, String amount) {
        return objectMapper.createObjectNode()
                .put("number", number)
                .put("counterpartyId", counterpartyId)
                .put("issueDate", issueDate.toString())
                .put("dueDate", dueDate.toString())
                .put("grossAmount", amount)
                .toString();
    }
}
