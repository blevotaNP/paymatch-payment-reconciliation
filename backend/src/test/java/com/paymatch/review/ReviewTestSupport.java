package com.paymatch.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymatch.invoice.Invoice;
import com.paymatch.invoice.InvoiceRepository;
import com.paymatch.matching.MatchSuggestion;
import com.paymatch.matching.MatchSuggestionRepository;
import com.paymatch.matching.SuggestionStatus;
import com.paymatch.statement.TestFiles;
import com.paymatch.transaction.BankTransaction;
import com.paymatch.transaction.BankTransactionRepository;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Loads the demo data through the API and finds things in it by human-readable keys. */
class ReviewTestSupport {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final InvoiceRepository invoiceRepository;
    private final BankTransactionRepository transactionRepository;
    private final MatchSuggestionRepository suggestionRepository;

    ReviewTestSupport(MockMvc mockMvc, ObjectMapper objectMapper, InvoiceRepository invoiceRepository,
                      BankTransactionRepository transactionRepository, MatchSuggestionRepository suggestionRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.invoiceRepository = invoiceRepository;
        this.transactionRepository = transactionRepository;
        this.suggestionRepository = suggestionRepository;
    }

    void importInvoices() throws Exception {
        upload("/api/invoices/import", "samples/invoices.csv");
    }

    JsonNode importStatement(String name) throws Exception {
        return upload("/api/imports", "samples/statements/" + name);
    }

    private JsonNode upload(String url, String location) throws Exception {
        String fileName = location.substring(location.lastIndexOf('/') + 1);
        MockMultipartFile file = new MockMultipartFile("file", fileName, "application/octet-stream", TestFiles.bytes(location));
        String body = mockMvc.perform(multipart(url).file(file).with(csrf())).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    Invoice invoice(String number) {
        return invoiceRepository.findAll().stream().filter(i -> i.getNumber().equals(number)).findFirst().orElseThrow();
    }

    BankTransaction transaction(String title) {
        return transactionRepository.findAll().stream().filter(t -> title.equals(t.getTitle())).findFirst().orElseThrow();
    }

    MatchSuggestion pendingSuggestion(String title, String invoiceNumber) {
        return suggestionRepository.findByTransactionIdOrderByScoreDesc(transaction(title).getId()).stream()
                .filter(s -> s.getStatus() == SuggestionStatus.PENDING)
                .filter(s -> s.getItems().stream().anyMatch(item -> item.getInvoice().getNumber().equals(invoiceNumber)))
                .findFirst().orElseThrow();
    }
}
