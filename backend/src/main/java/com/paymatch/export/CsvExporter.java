package com.paymatch.export;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV the way Polish Excel opens it without an import wizard: ';' as separator, ',' as decimal separator,
 * UTF-8 with BOM so Polish letters are not garbled.
 */
@Component
public class CsvExporter {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    public byte[] write(ExportTable table) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(UTF8_BOM);
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(';').setRecordSeparator("\r\n").get();
        try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(out, StandardCharsets.UTF_8), format)) {
            printer.printRecord(table.getColumns().stream().map(ExportTable.Column::header).toList());
            for (List<Object> row : table.getRows()) {
                List<String> values = new ArrayList<>(row.size());
                for (Object value : row) {
                    values.add(format(value));
                }
                printer.printRecord(values);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static String format(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal amount) {
            return amount.toPlainString().replace('.', ',');
        }
        return value.toString();
    }
}
