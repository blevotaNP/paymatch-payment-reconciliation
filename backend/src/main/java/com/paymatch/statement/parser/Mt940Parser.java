package com.paymatch.statement.parser;

import com.paymatch.common.Money;
import com.paymatch.common.parsing.AmountParser;
import com.paymatch.common.validation.Iban;
import com.paymatch.statement.BankFormat;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple MT940 reader for the fields we need: :25: account, :60F:/:62F: balances, :61: entries and the
 * :86: details that follow them. Polish banks put structured data into :86: as sub-fields
 * ("~20" title, "~32" name, "~38" IBAN...), the separator differs between banks (~, ^, <).
 */
@Component
public class Mt940Parser implements StatementParser {

    private static final Pattern TAG_LINE = Pattern.compile("^:(\\d{2}[A-Z]?):(.*)$");
    private static final Pattern ACCOUNT_TAG = Pattern.compile("(?m)^:25:");
    private static final Pattern ENTRY_OR_BALANCE_TAG = Pattern.compile("(?m)^:(61|60[FM]):");

    // value date YYMMDD, optional entry date MMDD, debit/credit mark, optional funds code, amount,
    // transaction type (e.g. S051, NTRF), customer reference, optional //bank reference
    private static final Pattern ENTRY = Pattern.compile(
            "^(\\d{6})(\\d{4})?(R?[CD])([A-Z])?(\\d+,\\d{0,2})([NSF][A-Z0-9]{3})([^/\\n]*)(?://([^\\n]*))?");
    private static final Pattern BALANCE = Pattern.compile("^([CD])(\\d{6})([A-Z]{3})(\\d+,\\d{0,2})");
    private static final Pattern STRUCTURED_86 = Pattern.compile("^(\\d{3})?([~^<?])\\d{2}");
    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    @Override
    public BankFormat format() {
        return BankFormat.MT940;
    }

    @Override
    public boolean supports(String content) {
        return ACCOUNT_TAG.matcher(content).find() && ENTRY_OR_BALANCE_TAG.matcher(content).find();
    }

    @Override
    public ParsedStatement parse(String content) {
        List<Tag> tags = readTags(content);
        if (tags.stream().noneMatch(tag -> tag.name().equals("25") || tag.name().equals("61"))) {
            throw new StatementParseException("File does not look like an MT940 statement (no :25: or :61: tags)");
        }

        ParsedStatement statement = new ParsedStatement();
        String currency = "PLN";
        BigDecimal openingBalance = null;
        BigDecimal blockSum = Money.ZERO;
        Tag pendingEntry = null;
        String pendingDetails = null;

        for (Tag tag : tags) {
            switch (tag.name()) {
                case "25" -> statement.setAccountIban(parseAccount(tag.value()));
                case "60F", "60M" -> {
                    Balance balance = parseBalance(tag);
                    if (balance != null) {
                        openingBalance = balance.amount();
                        currency = balance.currency();
                        statement.includePeriod(balance.date(), null);
                    }
                    blockSum = Money.ZERO;
                }
                case "61" -> {
                    blockSum = blockSum.add(addEntry(statement, pendingEntry, pendingDetails, currency));
                    pendingEntry = tag;
                    pendingDetails = null;
                }
                case "86" -> {
                    if (pendingEntry != null && pendingDetails == null) {
                        pendingDetails = tag.value();
                    }
                }
                case "62F", "62M" -> {
                    blockSum = blockSum.add(addEntry(statement, pendingEntry, pendingDetails, currency));
                    pendingEntry = null;
                    pendingDetails = null;
                    Balance closing = parseBalance(tag);
                    if (closing != null) {
                        statement.includePeriod(null, closing.date());
                        checkBalance(statement, openingBalance, blockSum, closing);
                    }
                }
                default -> {
                    // :20:, :28C:, :64:, :65: and others are not needed
                }
            }
        }
        // file cut off before :62F:
        if (pendingEntry != null) {
            addEntry(statement, pendingEntry, pendingDetails, currency);
            statement.warn("Statement has no closing balance (:62F:), the file may be incomplete");
        }
        return statement;
    }

    /** Returns the signed amount that was added, so the caller can check balances. */
    private BigDecimal addEntry(ParsedStatement statement, Tag entry, String details, String currency) {
        if (entry == null) {
            return Money.ZERO;
        }
        Matcher m = ENTRY.matcher(entry.value());
        if (!m.find()) {
            statement.skip(entry.line(), "Unrecognized :61: entry", entry.value());
            return Money.ZERO;
        }
        try {
            LocalDate valueDate = LocalDate.parse(m.group(1), YYMMDD);
            LocalDate bookingDate = m.group(2) != null ? entryDate(valueDate, m.group(2)) : valueDate;
            String mark = m.group(3);
            BigDecimal amount = AmountParser.parse(m.group(5));
            // D = debit, RC = reversal of credit; both take money out
            if (mark.equals("D") || mark.equals("RC")) {
                amount = amount.negate();
            }
            if (amount.signum() == 0) {
                statement.skip(entry.line(), "Zero amount", entry.value());
                return Money.ZERO;
            }

            Details parsedDetails = parseDetails(details);
            String title = parsedDetails.title() != null ? parsedDetails.title() : firstLine(supplementary(entry.value()));
            statement.addTransaction(new ParsedTransaction(
                    entry.line(),
                    bookingDate,
                    valueDate,
                    amount,
                    currency,
                    parsedDetails.name(),
                    parsedDetails.account(),
                    title,
                    bankReference(m.group(7), m.group(8)),
                    ":61:" + entry.value() + (details != null ? "\n:86:" + details : "")));
            return amount;
        } catch (DateTimeParseException | IllegalArgumentException e) {
            statement.skip(entry.line(), "Invalid :61: entry: " + e.getMessage(), entry.value());
            return Money.ZERO;
        }
    }

    private static LocalDate entryDate(LocalDate valueDate, String mmdd) {
        int month = Integer.parseInt(mmdd.substring(0, 2));
        int day = Integer.parseInt(mmdd.substring(2, 4));
        int year = valueDate.getYear();
        // the entry date has no year; around new year it can belong to the neighbouring year
        if (month == 12 && valueDate.getMonthValue() == 1) {
            year--;
        } else if (month == 1 && valueDate.getMonthValue() == 12) {
            year++;
        }
        return LocalDate.of(year, month, day);
    }

    private static String bankReference(String customerReference, String bankReference) {
        if (bankReference != null && !bankReference.isBlank()) {
            return bankReference.trim();
        }
        String reference = customerReference == null ? "" : customerReference.trim();
        return reference.isEmpty() || reference.equalsIgnoreCase("NONREF") ? null : reference;
    }

    private Details parseDetails(String details) {
        if (details == null || details.isBlank()) {
            return new Details(null, null, null);
        }
        // sub-fields are wrapped at fixed width, so continuation lines are joined without a space
        String joined = details.replace("\n", "");
        Matcher structured = STRUCTURED_86.matcher(joined);
        if (!structured.find()) {
            return new Details(clean(joined), null, null);
        }

        String separator = structured.group(2);
        Map<String, StringBuilder> fields = new TreeMap<>();
        for (String part : joined.split(Pattern.quote(separator))) {
            if (part.length() >= 2 && Character.isDigit(part.charAt(0)) && Character.isDigit(part.charAt(1))) {
                fields.computeIfAbsent(part.substring(0, 2), k -> new StringBuilder()).append(part.substring(2));
            }
        }

        String title = joinFields(fields, "20", "21", "22", "23", "24", "25", "26");
        String name = joinFields(fields, "27", "28", "29", "32", "33");
        String account = fields.containsKey("38")
                ? fields.get("38").toString()
                : joinFields(fields, "30", "31");
        return new Details(title, name, account == null ? null : Iban.normalize(account));
    }

    private static String joinFields(Map<String, StringBuilder> fields, String... codes) {
        StringBuilder result = new StringBuilder();
        for (String code : codes) {
            if (fields.containsKey(code)) {
                result.append(fields.get(code));
            }
        }
        return clean(result.toString());
    }

    private Balance parseBalance(Tag tag) {
        Matcher m = BALANCE.matcher(tag.value());
        if (!m.find()) {
            return null;
        }
        BigDecimal amount = AmountParser.parse(m.group(4));
        return new Balance(LocalDate.parse(m.group(2), YYMMDD), m.group(3),
                m.group(1).equals("D") ? amount.negate() : amount);
    }

    private static void checkBalance(ParsedStatement statement, BigDecimal opening, BigDecimal sum, Balance closing) {
        if (opening != null && opening.add(sum).compareTo(closing.amount()) != 0) {
            statement.warn("Balance mismatch in statement ending " + closing.date() + ": opening " + opening.toPlainString()
                    + " + transactions " + sum.toPlainString() + " != closing " + closing.amount().toPlainString()
                    + ". Some entries may be missing or unreadable.");
        }
    }

    private static String parseAccount(String value) {
        String account = value.trim();
        if (account.startsWith("/")) {
            account = account.substring(1);
        }
        // some banks append the currency: "PL61109010140000071219812874PLN"
        account = account.replaceAll("[A-Z]{3}$", "");
        return Iban.normalize(account);
    }

    private static List<Tag> readTags(String content) {
        String text = content
                .replaceAll("\\{[1235]:[^}]*}", "")
                .replace("{4:", "")
                .replace("-}", "-")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        List<Tag> tags = new ArrayList<>();
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher m = TAG_LINE.matcher(line);
            if (m.matches()) {
                tags.add(new Tag(m.group(1), m.group(2), i + 1));
            } else if (!line.isBlank() && !line.equals("-") && !tags.isEmpty()) {
                Tag last = tags.removeLast();
                tags.add(new Tag(last.name(), last.value() + "\n" + line, last.line()));
            }
        }
        return tags;
    }

    private static String supplementary(String entry) {
        int newline = entry.indexOf('\n');
        return newline < 0 ? null : entry.substring(newline + 1);
    }

    private static String firstLine(String value) {
        if (value == null) {
            return null;
        }
        int newline = value.indexOf('\n');
        return clean(newline < 0 ? value : value.substring(0, newline));
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private record Tag(String name, String value, long line) {
    }

    private record Balance(LocalDate date, String currency, BigDecimal amount) {
    }

    private record Details(String title, String name, String account) {
    }
}
