package com.paymatch.export;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A sheet of data in a format-neutral shape: column headers with a type, and rows of values
 * (String, LocalDate, BigDecimal, Long or null). The CSV and XLSX writers both read this.
 */
public class ExportTable {

    public enum Type {
        TEXT,
        DATE,
        MONEY,
        NUMBER
    }

    public record Column(String header, Type type, int width) {
    }

    private final String name;
    private final List<Column> columns = new ArrayList<>();
    private final List<List<Object>> rows = new ArrayList<>();
    private boolean lastRowIsTotal;

    public ExportTable(String name) {
        this.name = name;
    }

    public ExportTable column(String header, Type type, int width) {
        columns.add(new Column(header, type, width));
        return this;
    }

    public void row(Object... values) {
        if (values.length != columns.size()) {
            throw new IllegalArgumentException("Expected " + columns.size() + " values, got " + values.length);
        }
        rows.add(Arrays.asList(values));
    }

    /** Adds a summary row that the XLSX writer shows in bold. */
    public void totalRow(Object... values) {
        row(values);
        lastRowIsTotal = true;
    }

    public String getName() {
        return name;
    }

    public List<Column> getColumns() {
        return columns;
    }

    public List<List<Object>> getRows() {
        return rows;
    }

    public boolean isLastRowTotal() {
        return lastRowIsTotal;
    }
}
