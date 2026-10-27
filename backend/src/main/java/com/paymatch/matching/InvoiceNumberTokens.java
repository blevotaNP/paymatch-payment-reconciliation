package com.paymatch.matching;

import com.paymatch.common.TextNormalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits text into letter and number tokens so invoice numbers can be compared regardless of how people
 * write them: "FV/10/2026", "fv 10/2026", "FV10-2026" and "FV/010/2026" all give [FV, 10, 2026].
 * Numbers are compared without leading zeros. A group that looks like a number with a typo, such as "1O"
 * (letter O instead of zero), is read as a number and marked as corrected.
 */
public final class InvoiceNumberTokens {

    private static final Pattern GROUP = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern LETTERS_OR_DIGITS = Pattern.compile("[A-Z]+|[0-9]+");
    private static final Pattern DIGIT_LIKE = Pattern.compile("[0-9OIL]+");

    private InvoiceNumberTokens() {
    }

    /**
     * @param joinedToPrevious true when only "/", "-", "." or nothing separates the token from the previous one,
     *                         i.e. the two tokens are parts of the same identifier
     */
    public record Token(String value, boolean numeric, boolean corrected, int start, int end, boolean joinedToPrevious) {

        boolean sameAs(Token other) {
            return numeric == other.numeric && value.equals(other.value);
        }
    }

    public static List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        Matcher group = GROUP.matcher(text);
        int previousEnd = -1;
        while (group.find()) {
            String upper = TextNormalizer.upperAscii(group.group());
            boolean joined = previousEnd >= 0 && isJoiningSeparator(text.substring(previousEnd, group.start()));

            if (looksLikeNumberWithTypo(upper)) {
                String fixed = upper.replace('O', '0').replace('I', '1').replace('L', '1');
                tokens.add(new Token(stripLeadingZeros(fixed), true, true, group.start(), group.end(), joined));
            } else {
                Matcher part = LETTERS_OR_DIGITS.matcher(upper);
                boolean first = true;
                while (part.find()) {
                    boolean numeric = Character.isDigit(part.group().charAt(0));
                    String value = numeric ? stripLeadingZeros(part.group()) : part.group();
                    // parts split out of one group ("FV10") are always joined
                    tokens.add(new Token(value, numeric, false, group.start(), group.end(), !first || joined));
                    first = false;
                }
            }
            previousEnd = group.end();
        }
        return tokens;
    }

    private static boolean looksLikeNumberWithTypo(String group) {
        return DIGIT_LIKE.matcher(group).matches()
                && group.chars().anyMatch(Character::isDigit)
                && group.chars().anyMatch(Character::isLetter);
    }

    private static boolean isJoiningSeparator(String separator) {
        return separator.isEmpty() || separator.matches("[/\\-.\\\\_]+");
    }

    private static String stripLeadingZeros(String digits) {
        String stripped = digits.replaceFirst("^0+", "");
        return stripped.isEmpty() ? "0" : stripped;
    }
}
