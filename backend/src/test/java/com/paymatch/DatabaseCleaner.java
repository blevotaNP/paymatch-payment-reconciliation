package com.paymatch;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/** Empties all business tables between integration tests. Users stay, they are created once at startup. */
@TestComponent
public class DatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clean() {
        jdbcTemplate.execute("""
                TRUNCATE audit_event, allocation, match_suggestion_item, match_suggestion, bank_transaction,
                         statement_import, invoice, counterparty RESTART IDENTITY CASCADE
                """);
    }
}
