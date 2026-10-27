package com.paymatch.statement.parser;

import com.paymatch.statement.BankFormat;

/**
 * One implementation per bank export format. Adding a bank means adding a class that implements this
 * interface and annotating it with {@code @Component}.
 */
public interface StatementParser {

    BankFormat format();

    /** Quick look at the decoded content, used to auto-detect the format. Must not throw. */
    boolean supports(String content);

    /**
     * Parses the whole file. Rows that cannot be read are reported via {@link ParsedStatement#skip};
     * a {@link StatementParseException} is thrown only when the file as a whole is unreadable.
     */
    ParsedStatement parse(String content);
}
