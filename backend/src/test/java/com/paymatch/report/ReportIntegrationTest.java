package com.paymatch.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymatch.DatabaseCleaner;
import com.paymatch.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Due dates are relative to today, so the buckets stay the same whenever the test runs. */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "anna")
class ReportIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Warsaw"));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() throws Exception {
        databaseCleaner.clean();
        long acme = counterparty("ACME Sp. z o.o.");
        long kowalski = counterparty("Kowalski i Syn");
        invoice("FV/1", acme, 5, "100.00");        // not due
        invoice("FV/2", acme, -10, "200.00");      // 1-30
        invoice("FV/3", acme, -45, "300.00");      // 31-60
        invoice("FV/4", kowalski, -75, "400.00");  // 61-90
        invoice("FV/5", kowalski, -120, "500.00"); // 90+
        invoice("FV/6", kowalski, -30, "600.00");  // exactly 30 days: still 1-30
    }

    @Test
    void agingReportGroupsOpenAmountsByCounterpartyAndBucket() throws Exception {
        mockMvc.perform(get("/api/reports/aging"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value(TODAY.toString()))
                .andExpect(jsonPath("$.total").value("2100.00"))
                .andExpect(jsonPath("$.totals.NOT_DUE").value("100.00"))
                .andExpect(jsonPath("$.totals.DAYS_1_30").value("800.00"))
                .andExpect(jsonPath("$.totals.DAYS_31_60").value("300.00"))
                .andExpect(jsonPath("$.totals.DAYS_61_90").value("400.00"))
                .andExpect(jsonPath("$.totals.DAYS_90_PLUS").value("500.00"))
                // Kowalski owes more overdue money, so comes first
                .andExpect(jsonPath("$.rows[0].counterpartyName").value("Kowalski i Syn"))
                .andExpect(jsonPath("$.rows[0].total").value("1500.00"))
                .andExpect(jsonPath("$.rows[0].invoices[0].number").value("FV/5"))
                .andExpect(jsonPath("$.rows[0].invoices[0].daysOverdue").value(120))
                .andExpect(jsonPath("$.rows[1].buckets.NOT_DUE").value("100.00"));
    }

    @Test
    void dashboardShowsOpenAndOverdueReceivables() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openInvoices.count").value(6))
                .andExpect(jsonPath("$.openInvoices.amount").value("2100.00"))
                .andExpect(jsonPath("$.overdueInvoices.count").value(5))
                .andExpect(jsonPath("$.overdueInvoices.amount").value("2000.00"))
                .andExpect(jsonPath("$.months.length()").value(6))
                .andExpect(jsonPath("$.topOverdue[0].name").value("Kowalski i Syn"))
                .andExpect(jsonPath("$.topOverdue[0].maxDaysOverdue").value(120))
                .andExpect(jsonPath("$.matching.incoming").value(0))
                .andExpect(jsonPath("$.recentActivity[0].action").value("INVOICE_CREATED"));
    }

    private long counterparty(String name) throws Exception {
        String body = mockMvc.perform(post("/api/counterparties").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("name", name).toString()))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void invoice(String number, long counterpartyId, int dueInDays, String amount) throws Exception {
        LocalDate due = TODAY.plusDays(dueInDays);
        mockMvc.perform(post("/api/invoices").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("number", number)
                                .put("counterpartyId", counterpartyId)
                                .put("issueDate", due.minusDays(14).toString())
                                .put("dueDate", due.toString())
                                .put("grossAmount", amount)
                                .toString()))
                .andExpect(status().isCreated());
    }
}
