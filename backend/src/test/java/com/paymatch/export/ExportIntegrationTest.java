package com.paymatch.export;

import com.paymatch.DatabaseCleaner;
import com.paymatch.IntegrationTest;
import com.paymatch.statement.TestFiles;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "anna")
class ExportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() throws Exception {
        databaseCleaner.clean();
        upload("/api/invoices/import", "samples/invoices.csv");
        upload("/api/imports", "samples/statements/mt940_pekao_2026-09.sta");
        upload("/api/imports", "samples/statements/mbank_lista_operacji_2026-10.csv");
    }

    @Test
    void reconciliationCsvOpensInPolishExcel() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/exports/reconciliation").param("format", "CSV"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", matchesPattern(
                        "attachment; filename=\"paymatch-reconciliation-\\d{4}-\\d{2}-\\d{2}\\.csv\"")))
                .andReturn();
        byte[] bytes = result.getResponse().getContentAsByteArray();

        assertThat(Arrays.copyOf(bytes, 3)).as("UTF-8 BOM").containsExactly(0xEF, 0xBB, 0xBF);
        List<String> lines = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8).lines().toList();
        assertThat(lines.getFirst()).startsWith("Booking date;Amount;Currency;Sender;");
        // only incoming payments: 3 from MT940 + 4 from mBank
        assertThat(lines).hasSize(1 + 7);
        assertThat(lines).anySatisfy(line -> assertThat(line)
                .startsWith("2026-09-14;1230,50;PLN;")
                .contains(";matched;FV/1/09/2026;1230,50;Automatic;"));
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("ZAKŁAD STOLARSKI WIŚNIEWSKI").contains(";to review;"));
    }

    @Test
    void reconciliationXlsxHasPaymentsAndInvoicesSheets() throws Exception {
        byte[] bytes = mockMvc.perform(get("/api/exports/reconciliation").param("from", "2026-10-01").param("to", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("Payments");
            assertThat(workbook.getSheetName(1)).isEqualTo("Invoices");

            Sheet payments = workbook.getSheet("Payments");
            assertThat(payments.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Booking date");
            // October only: the 4 incoming mBank payments
            assertThat(payments.getLastRowNum()).isEqualTo(4);
            Row acme = findRow(payments, 5, "FV/4/10/2026");
            assertThat(acme.getCell(1).getNumericCellValue()).isEqualTo(2460.00);
            assertThat(acme.getCell(1).getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
            assertThat(acme.getCell(0).getLocalDateTimeCellValue().toLocalDate()).hasToString("2026-10-23");

            assertThat(workbook.getSheet("Invoices").getLastRowNum()).isEqualTo(15);
        }
    }

    @Test
    void agingXlsxEndsWithTotalRow() throws Exception {
        byte[] bytes = mockMvc.perform(get("/api/exports/aging").param("format", "XLSX"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Not yet due");
            assertThat(header.getCell(7).getStringCellValue()).isEqualTo("Total");
            Row total = sheet.getRow(sheet.getLastRowNum());
            assertThat(total.getCell(0).getStringCellValue()).isEqualTo("Total PLN");
            assertThat(workbook.getFontAt(total.getCell(7).getCellStyle().getFontIndex()).getBold()).isTrue();
        }
    }

    @Test
    void invoicesCsvUsesCommaDecimals() throws Exception {
        String csv = mockMvc.perform(get("/api/exports/invoices").param("format", "CSV"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(csv).contains("FV/1/09/2026;ACME Sp. z o.o.;5260181596;2026-09-01;2026-09-15;1230,50;1230,50;0,00;PLN;paid;0");
    }

    @Test
    void unknownFormatIsRejected() throws Exception {
        mockMvc.perform(get("/api/exports/invoices").param("format", "PDF")).andExpect(status().isBadRequest());
    }

    private static Row findRow(Sheet sheet, int column, String value) {
        for (Row row : sheet) {
            if (row.getCell(column) != null && value.equals(row.getCell(column).getStringCellValue())) {
                return row;
            }
        }
        throw new AssertionError("No row with " + value);
    }

    private void upload(String url, String location) throws Exception {
        String fileName = location.substring(location.lastIndexOf('/') + 1);
        mockMvc.perform(multipart(url).file(new MockMultipartFile("file", fileName, "application/octet-stream",
                TestFiles.bytes(location))).with(csrf()));
    }
}
