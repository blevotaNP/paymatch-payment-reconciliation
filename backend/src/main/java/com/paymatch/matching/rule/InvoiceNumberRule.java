package com.paymatch.matching.rule;

import com.paymatch.common.Money;
import com.paymatch.matching.Identification;
import com.paymatch.matching.InvoiceNumberMatcher;
import com.paymatch.matching.InvoiceNumberMatcher.NumberMatch;
import com.paymatch.matching.InvoiceNumberTokens;
import com.paymatch.matching.InvoiceNumberTokens.Token;
import com.paymatch.matching.InvoiceSnapshot;
import com.paymatch.matching.MatchCandidate;
import com.paymatch.matching.MatchReason;
import com.paymatch.matching.MatchRule;
import com.paymatch.matching.MatchingContext;
import com.paymatch.transaction.BankTransaction;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Invoice number found in the title. Scores:
 * exact amount 100 (85-90 when the number was written without prefix or with a typo),
 * several numbers whose sum equals the amount 90, partial payment 70, overpayment 65, already paid 40.
 * The sender's account adds 5 when it belongs to the invoice's counterparty and takes 15 off when it belongs to someone else.
 */
@Component
@Order(1)
public class InvoiceNumberRule implements MatchingRule {

    @Override
    public List<MatchCandidate> apply(BankTransaction tx, MatchingContext context) {
        String title = tx.getTitle();
        List<Token> tokens = InvoiceNumberTokens.tokenize(title);
        if (tokens.isEmpty()) {
            return List.of();
        }

        Map<InvoiceSnapshot, NumberMatch> open = new LinkedHashMap<>();
        Map<InvoiceSnapshot, NumberMatch> paid = new LinkedHashMap<>();
        for (InvoiceSnapshot invoice : context.invoices(tx.getCurrency())) {
            InvoiceNumberMatcher.find(invoice, tokens, title, context.invoicePrefixes())
                    .ifPresent(match -> (invoice.isOpen() ? open : paid).put(invoice, match));
        }
        Optional<Identification> sender = context.identify(tx);

        List<MatchCandidate> candidates = new ArrayList<>();
        if (open.size() > 1) {
            multipleInvoices(tx, open).ifPresent(candidates::add);
        }
        open.forEach((invoice, match) -> candidates.add(single(tx, invoice, match, sender)));
        if (open.isEmpty()) {
            paid.forEach((invoice, match) -> candidates.add(alreadyPaid(tx, invoice, match)));
        }
        return candidates;
    }

    private MatchCandidate single(BankTransaction tx, InvoiceSnapshot invoice, NumberMatch match, Optional<Identification> sender) {
        List<MatchReason> reasons = new ArrayList<>();
        reasons.add(titleReason(invoice, match));

        BigDecimal amount = tx.getAmount();
        BigDecimal remaining = invoice.remainingAmount();
        int comparison = amount.compareTo(remaining);
        MatchRule rule;
        int score;
        if (comparison == 0) {
            rule = MatchRule.EXACT;
            score = match.isPerfect() ? 100 : match.corrected() ? 90 : 85;
            reasons.add(new MatchReason("amount", "Amount " + money(amount, tx) + " equals the amount left to pay"));
        } else if (comparison < 0) {
            rule = MatchRule.PARTIAL_PAYMENT;
            score = match.isPerfect() ? 70 : 60;
            reasons.add(new MatchReason("amount", "Amount " + money(amount, tx) + " is " + money(remaining.subtract(amount), tx)
                    + " less than the " + money(remaining, tx) + " left to pay"));
        } else {
            rule = MatchRule.OVERPAYMENT;
            score = match.isPerfect() ? 65 : 55;
            reasons.add(new MatchReason("amount", "Amount " + money(amount, tx) + " is " + money(amount.subtract(remaining), tx)
                    + " more than the " + money(remaining, tx) + " left to pay"));
        }

        if (sender.isPresent() && sender.get().isStrong()) {
            if (sender.get().counterparty().id().equals(invoice.getCounterpartyId())) {
                score += 5;
                reasons.add(sender.get().reason());
            } else {
                score -= 15;
                reasons.add(new MatchReason("account", "Sender is " + sender.get().counterparty().name()
                        + ", but the invoice was issued to " + invoice.getCounterpartyName()));
            }
        }
        // never more than 100, and the +5 must not lift a non-perfect exact match into auto-confirm territory
        if (rule == MatchRule.EXACT && !match.isPerfect()) {
            score = Math.min(score, 94);
        }
        return new MatchCandidate(rule, score, List.of(new MatchCandidate.Item(invoice, amount)), reasons);
    }

    private Optional<MatchCandidate> multipleInvoices(BankTransaction tx, Map<InvoiceSnapshot, NumberMatch> open) {
        BigDecimal sum = open.keySet().stream().map(InvoiceSnapshot::remainingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(tx.getAmount()) != 0) {
            return Optional.empty();
        }
        List<MatchReason> reasons = new ArrayList<>();
        open.forEach((invoice, match) -> reasons.add(titleReason(invoice, match)));
        reasons.add(new MatchReason("amount", "Amount " + money(tx.getAmount(), tx) + " equals the sum left to pay on "
                + open.size() + " invoices"));
        boolean allPerfect = open.values().stream().allMatch(NumberMatch::isPerfect);
        List<MatchCandidate.Item> items = open.keySet().stream()
                .map(invoice -> new MatchCandidate.Item(invoice, invoice.remainingAmount()))
                .toList();
        return Optional.of(new MatchCandidate(MatchRule.MULTIPLE_INVOICES, allPerfect ? 90 : 85, items, reasons));
    }

    private MatchCandidate alreadyPaid(BankTransaction tx, InvoiceSnapshot invoice, NumberMatch match) {
        return new MatchCandidate(MatchRule.ALREADY_PAID, 40,
                List.of(new MatchCandidate.Item(invoice, tx.getAmount())),
                List.of(titleReason(invoice, match),
                        new MatchReason("invoice", "Invoice " + invoice.getNumber() + " is already paid ("
                                + money(invoice.getPaidAmount(), tx) + "); this may be a double payment")));
    }

    private static MatchReason titleReason(InvoiceSnapshot invoice, NumberMatch match) {
        String message = "Invoice number " + invoice.getNumber() + " found in the title as \"" + match.foundText() + "\"";
        if (match.corrected()) {
            message += " (after fixing a typo, e.g. letter O instead of zero)";
        } else if (match.withoutPrefix()) {
            message += " (without the " + invoice.prefix() + " prefix)";
        }
        return new MatchReason("title", message);
    }

    private static String money(BigDecimal amount, BankTransaction tx) {
        return Money.format(amount, tx.getCurrency());
    }
}
