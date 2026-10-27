package com.paymatch.statement.parser;

public record SkippedRow(long line, String reason, String content) {
}
