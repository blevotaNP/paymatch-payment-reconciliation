package com.paymatch.matching;

import com.paymatch.matching.InvoiceNumberTokens.Token;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Looks for one invoice's number in the tokens of a transfer title. */
public final class InvoiceNumberMatcher {

    private InvoiceNumberMatcher() {
    }

    /**
     * @param foundText    the part of the title that matched, as the payer wrote it
     * @param withoutPrefix only the numeric part matched ("2/09/2026" for invoice "FV/2/09/2026")
     * @param corrected    a typo like letter O instead of zero was fixed to get the match
     */
    public record NumberMatch(String foundText, boolean withoutPrefix, boolean corrected) {

        public boolean isPerfect() {
            return !withoutPrefix && !corrected;
        }
    }

    /**
     * @param otherPrefixes letter prefixes used by invoice series in the system (e.g. "KOR" for corrections);
     *                      a numeric-only match directly after one of them belongs to that other series
     */
    public static Optional<NumberMatch> find(InvoiceSnapshot invoice, List<Token> title, String text, Set<String> otherPrefixes) {
        List<Token> number = invoice.getTokens();
        if (number.isEmpty()) {
            return Optional.empty();
        }
        Optional<NumberMatch> full = findSequence(number, title, text, otherPrefixes, false);
        if (full.isPresent()) {
            return full;
        }
        // without the letter prefix we need at least two numeric parts, "2/09/2026" yes, "2026" alone no
        List<Token> numericPart = number.subList(invoice.getPrefixLength(), number.size());
        if (invoice.getPrefixLength() > 0 && numericPart.size() >= 2) {
            return findSequence(numericPart, title, text, otherPrefixes, true);
        }
        return Optional.empty();
    }

    private static Optional<NumberMatch> findSequence(List<Token> wanted, List<Token> title, String text,
                                                      Set<String> otherPrefixes, boolean withoutPrefix) {
        for (int start = 0; start + wanted.size() <= title.size(); start++) {
            if (!matchesAt(wanted, title, start)) {
                continue;
            }
            int end = start + wanted.size() - 1;
            if (isPartOfLongerIdentifier(title, start, end, otherPrefixes)) {
                continue;
            }
            boolean corrected = title.subList(start, end + 1).stream().anyMatch(Token::corrected);
            String found = text.substring(title.get(start).start(), title.get(end).end());
            return Optional.of(new NumberMatch(found, withoutPrefix, corrected));
        }
        return Optional.empty();
    }

    private static boolean matchesAt(List<Token> wanted, List<Token> title, int start) {
        for (int i = 0; i < wanted.size(); i++) {
            if (!wanted.get(i).sameAs(title.get(start + i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * "FV/1/10/2026" must not match inside "FV/11/10/2026" (tokens differ anyway), and "10/2026" must not match
     * inside "FV/1/10/2026" - that is caught here by checking the tokens glued to both ends of the match.
     */
    private static boolean isPartOfLongerIdentifier(List<Token> title, int start, int end, Set<String> otherPrefixes) {
        Token first = title.get(start);
        if (start > 0 && first.joinedToPrevious()) {
            Token before = title.get(start - 1);
            if (before.numeric() || otherPrefixes.contains(before.value())) {
                return true;
            }
        }
        if (end + 1 < title.size()) {
            Token after = title.get(end + 1);
            return after.joinedToPrevious() && after.numeric();
        }
        return false;
    }
}
