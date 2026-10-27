package com.paymatch.statement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymatch.IntegrationTest;
import com.paymatch.transaction.BankTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "anna")
class StatementImportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BankTransactionRepository transactionRepository;
    @Autowired
    private StatementImportRepository importRepository;

    @BeforeEach
    void cleanDatabase() {
        transactionRepository.deleteAllInBatch();
        importRepository.deleteAllInBatch();
    }

    @Test
    void importsMt940AndDetectsFormat() throws Exception {
        upload("mt940_pekao_2026-09.sta")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bankFormat").value("MT940"))
                .andExpect(jsonPath("$.accountIban").value("PL04124010371111001122334455"))
                .andExpect(jsonPath("$.periodFrom").value("2026-09-01"))
                .andExpect(jsonPath("$.periodTo").value("2026-09-30"))
                .andExpect(jsonPath("$.encoding").value("windows-1250"))
                .andExpect(jsonPath("$.importedCount").value(5))
                .andExpect(jsonPath("$.duplicateCount").value(0))
                .andExpect(jsonPath("$.importedBy").value("Anna Kowalska"));

        assertThat(transactionRepository.count()).isEqualTo(5);
    }

    @Test
    void reimportingTheSameFileCreatesNoDuplicates() throws Exception {
        upload("mt940_pekao_2026-09.sta").andExpect(status().isCreated());

        upload("mt940_pekao_2026-09.sta")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importedCount").value(0))
                .andExpect(jsonPath("$.duplicateCount").value(5))
                .andExpect(jsonPath("$.duplicates.length()").value(5))
                .andExpect(jsonPath("$.warnings[0]").value(startsWith("This exact file was already imported")));

        assertThat(transactionRepository.count()).isEqualTo(5);
    }

    @Test
    void overlappingPeriodOnlyAddsNewTransactions() throws Exception {
        upload("mt940_pekao_2026-09.sta").andExpect(status().isCreated());

        upload("mt940_pekao_2026-09-25_2026-10-10.sta")
                .andExpect(jsonPath("$.importedCount").value(3))
                .andExpect(jsonPath("$.duplicateCount").value(2));

        assertThat(transactionRepository.count()).isEqualTo(8);
    }

    @Test
    void keepsTwoIdenticalFeesFromCsvWithoutReferenceButDetectsThemOnReimport() throws Exception {
        upload("pko_bp_historia_2026-10.csv")
                .andExpect(jsonPath("$.bankFormat").value("PKO_BP_CSV"))
                .andExpect(jsonPath("$.importedCount").value(7));

        upload("pko_bp_historia_2026-10.csv")
                .andExpect(jsonPath("$.importedCount").value(0))
                .andExpect(jsonPath("$.duplicateCount").value(7));
    }

    @Test
    void reportsSkippedRowsInSummaryAndHistory() throws Exception {
        long id = idOf(upload("ing_lista_transakcji_2026-10.csv")
                .andExpect(jsonPath("$.importedCount").value(4))
                .andExpect(jsonPath("$.skippedCount").value(1))
                .andExpect(jsonPath("$.totalRows").value(5)));

        mockMvc.perform(get("/api/imports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skippedRows[0].line").value(16))
                .andExpect(jsonPath("$.skippedRows[0].reason").value(startsWith("Card authorization")))
                .andExpect(jsonPath("$.duplicates").doesNotExist());
    }

    @Test
    void undoRemovesTransactionsAndAllowsImportingAgain() throws Exception {
        long id = idOf(upload("mbank_lista_operacji_2026-10.csv").andExpect(jsonPath("$.importedCount").value(6)));

        mockMvc.perform(post("/api/imports/{id}/undo", id).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDONE"))
                .andExpect(jsonPath("$.undoneBy").value("Anna Kowalska"));
        assertThat(transactionRepository.count()).isZero();

        upload("mbank_lista_operacji_2026-10.csv")
                .andExpect(jsonPath("$.importedCount").value(6))
                .andExpect(jsonPath("$.warnings.length()").value(0));

        mockMvc.perform(post("/api/imports/{id}/undo", id).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void usesRequestedFormatAndRejectsUnknownFiles() throws Exception {
        upload("statements/broken/random_text.txt", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(startsWith("Could not recognise the bank format")));

        upload("statements/broken/pko_missing_amount_column.csv", "PKO_BP_CSV")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Could not read statement"));

        upload("statements/broken/random_text.txt", "MT940")
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsTransactionsFilteredByDirectionAndImport() throws Exception {
        long id = idOf(upload("mbank_lista_operacji_2026-10.csv"));
        upload("ing_lista_transakcji_2026-10.csv");

        mockMvc.perform(get("/api/transactions").param("importId", String.valueOf(id)).param("direction", "INCOMING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].bookingDate").value("2026-10-26"))
                .andExpect(jsonPath("$.content[0].amount").value("5600.00"));

        mockMvc.perform(get("/api/transactions").param("search", "kowalczyk"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Platnosc za faktury"));
    }

    private ResultActions upload(String sampleName) throws Exception {
        return upload("samples/statements/" + sampleName, null);
    }

    private ResultActions upload(String classpathLocation, String format) throws Exception {
        String fileName = classpathLocation.substring(classpathLocation.lastIndexOf('/') + 1);
        MockMultipartFile file = new MockMultipartFile("file", fileName, "application/octet-stream",
                TestFiles.bytes(classpathLocation));
        var request = multipart("/api/imports").file(file).with(csrf());
        if (format != null) {
            request.param("format", format);
        }
        return mockMvc.perform(request);
    }

    private long idOf(ResultActions result) throws Exception {
        JsonNode body = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return body.get("id").asLong();
    }
}
