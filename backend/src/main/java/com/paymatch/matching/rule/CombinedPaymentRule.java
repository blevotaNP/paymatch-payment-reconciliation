package com.paymatch.matching.rule;

import com.paymatch.common.Money;
import com.paymatch.matching.Identification;
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
import java.util.List;
import java.util.Optional;

/**
 * The sender is a known counterparty and the amount equals the sum of 2 to 4 of their open invoices.
 * Score 70 (account/NIP) or 60 (name), 15 less when more than one combination adds up.
 * Only the 12 oldest open invoices are searched, which keeps it to under 800 combinations.
 */
@Component
@Order(3)
public class CombinedPaymentRule implements MatchingRule {

    static final int MAX_INVOICES = 12;
    static final int MAX_COMBINATION_SIZE = 4;

    @Override
    public List<MatchCandidate> apply(BankTransaction tx, MatchingContext context) {
        Optional<Identification> sender = context.identify(tx);
        if (sender.isEmpty()) {
            return List.of();
        }
        List<InvoiceSnapshot> open = context.openInvoicesOf(sender.get().counterparty().id(), tx.getCurrency());
        if (open.size() < 2) {
            return List.of();
        }
        List<InvoiceSnapshot> searched = open.subList(0, Math.min(open.size(), MAX_INVOICES));

        List<List<InvoiceSnapshot>> found = new ArrayList<>();
        search(searched, 0, new ArrayList<>(), BigDecimal.ZERO, tx.getAmount(), found);
        if (found.isEmpty()) {
            return List.of();
        }

        List<InvoiceSnapshot> combination = found.getFirst();
        List<MatchReason> reasons = new ArrayList<>();
        reasons.add(sender.get().reason());
        reasons.add(new MatchReason("amount", "Amount " + Money.format(tx.getAmount(), tx.getCurrency())
                + " equals the sum left to pay on " + String.join(" + ",
                combination.stream().map(InvoiceSnapshot::getNumber).toList())));
        int score = sender.get().isStrong() ? 70 : 60;
        if (found.size() > 1) {
            score -= 15;
            reasons.add(new MatchReason("invoice", "Other combinations of invoices give the same sum; the oldest ones were chosen"));
        }
        List<MatchCandidate.Item> items = combination.stream()
                .map(invoice -> new MatchCandidate.Item(invoice, invoice.remainingAmount()))
                .toList();
        return List.of(new MatchCandidate(MatchRule.COMBINED_PAYMENT, score, items, reasons));
    }

    /** Depth-first over combinations in due-date order; stops after finding two (enough to know it is ambiguous). */
    private void search(List<InvoiceSnapshot> invoices, int from, List<InvoiceSnapshot> current, BigDecimal sum,
                        BigDecimal target, List<List<InvoiceSnapshot>> found) {
        if (found.size() >= 2) {
            return;
        }
        int comparison = sum.compareTo(target);
        if (comparison == 0 && current.size() >= 2) {
            found.add(List.copyOf(current));
            return;
        }
        if (comparison > 0 || current.size() == MAX_COMBINATION_SIZE) {
            return;
        }
        for (int i = from; i < invoices.size(); i++) {
            InvoiceSnapshot invoice = invoices.get(i);
            current.add(invoice);
            search(invoices, i + 1, current, sum.add(invoice.remainingAmount()), target, found);
            current.removeLast();
        }
    }
}
