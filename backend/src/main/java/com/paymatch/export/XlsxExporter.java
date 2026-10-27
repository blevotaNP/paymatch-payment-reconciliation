package com.paymatch.export;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
public class XlsxExporter {

    public byte[] write(List<ExportTable> tables) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            for (ExportTable table : tables) {
                writeSheet(workbook, table, styles);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeSheet(XSSFWorkbook workbook, ExportTable table, Styles styles) {
        Sheet sheet = workbook.createSheet(table.getName());
        List<ExportTable.Column> columns = table.getColumns();

        Row header = sheet.createRow(0);
        for (int c = 0; c < columns.size(); c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(columns.get(c).header());
            cell.setCellStyle(styles.header);
            // width is given in characters; POI counts in 1/256 of a character
            sheet.setColumnWidth(c, columns.get(c).width() * 256);
        }

        List<List<Object>> rows = table.getRows();
        for (int r = 0; r < rows.size(); r++) {
            boolean total = table.isLastRowTotal() && r == rows.size() - 1;
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < columns.size(); c++) {
                writeCell(row.createCell(c), rows.get(r).get(c), columns.get(c).type(), styles, total);
            }
        }

        sheet.createFreezePane(0, 1);
        int lastDataRow = table.isLastRowTotal() ? rows.size() - 1 : rows.size();
        if (lastDataRow > 0) {
            sheet.setAutoFilter(new CellRangeAddress(0, lastDataRow, 0, columns.size() - 1));
        }
    }

    private void writeCell(Cell cell, Object value, ExportTable.Type type, Styles styles, boolean total) {
        if (value == null) {
            cell.setCellStyle(total ? styles.totalText : styles.text);
            return;
        }
        switch (type) {
            case MONEY -> {
                // Excel stores every number as a double; converting here, at the very end, is the only place we do it
                cell.setCellValue(((BigDecimal) value).doubleValue());
                cell.setCellStyle(total ? styles.totalMoney : styles.money);
            }
            case DATE -> {
                cell.setCellValue((LocalDate) value);
                cell.setCellStyle(styles.date);
            }
            case NUMBER -> {
                cell.setCellValue(((Number) value).doubleValue());
                cell.setCellStyle(styles.number);
            }
            default -> {
                cell.setCellValue(value.toString());
                cell.setCellStyle(total ? styles.totalText : styles.text);
            }
        }
    }

    private static final class Styles {
        final CellStyle header;
        final CellStyle text;
        final CellStyle money;
        final CellStyle date;
        final CellStyle number;
        final CellStyle totalText;
        final CellStyle totalMoney;

        Styles(XSSFWorkbook workbook) {
            Font bold = workbook.createFont();
            bold.setBold(true);

            header = workbook.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);

            text = workbook.createCellStyle();

            money = workbook.createCellStyle();
            money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            date = workbook.createCellStyle();
            date.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));

            number = workbook.createCellStyle();
            number.setDataFormat(workbook.createDataFormat().getFormat("0"));

            totalText = workbook.createCellStyle();
            totalText.setFont(bold);
            totalText.setBorderTop(BorderStyle.THIN);

            totalMoney = workbook.createCellStyle();
            totalMoney.cloneStyleFrom(money);
            totalMoney.setFont(bold);
            totalMoney.setBorderTop(BorderStyle.THIN);
        }
    }
}
