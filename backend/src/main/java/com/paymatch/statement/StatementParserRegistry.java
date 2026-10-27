package com.paymatch.statement;

import com.paymatch.statement.parser.StatementParser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class StatementParserRegistry {

    private final List<StatementParser> parsers;

    public StatementParserRegistry(List<StatementParser> parsers) {
        this.parsers = parsers;
    }

    public StatementParser get(BankFormat format) {
        return parsers.stream()
                .filter(parser -> parser.format() == format)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No parser for " + format));
    }

    /** Picks the parser whose header/tag check recognises the file. The checks are specific enough not to overlap. */
    public Optional<StatementParser> detect(String content) {
        return parsers.stream().filter(parser -> parser.supports(content)).findFirst();
    }
}
