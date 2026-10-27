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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * No invoice number needed: the sender is a known counterparty and the amount equals what is left to pay
 * on one of their open invoices. Score 75 when recognised by account or NIP, 60 by name.
 * When several invoices have that amount, each one is suggested with 25 points less, oldest due date first.
 */
@Component
@Order(2)
public class CounterpartyAmountRule implements MatchingRule {

    @Override
    public List<MatchCandidate> apply(BankTransaction tx, MatchingContext context) {
        Optional<Identification> sender = context.identify(tx);
        if (sender.isEmpty()) {
            return List.of();
        }
        Identification identification = sender.get();
        List<InvoiceSnapshot> sameAmount = context.openInvoicesOf(identification.counterparty().id(), tx.getCurrency()).stream()
                .filter(invoice -> invoice.remainingAmount().compareTo(tx.getAmount()) == 0)
                .toList();
        if (sameAmount.isEmpty()) {
            return List.of();
        }

        int base = identification.isStrong() ? 75 : 60;
        List<MatchCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < sameAmount.size(); i++) {
            InvoiceSnapshot invoice = sameAmount.get(i);
            List<MatchReason> reasons = new ArrayList<>();
            reasons.add(identification.reason());
            reasons.add(new MatchReason("amount", "Amount " + Money.format(tx.getAmount(), tx.getCurrency())
                    + " equals the amount left to pay on " + invoice.getNumber()));
            int score = base;
            if (sameAmount.size() > 1) {
                score = base - 25 - i * 2;
                reasons.add(new MatchReason("invoice", sameAmount.size() + " open invoices of this counterparty have the same amount"));
            }
            candidates.add(new MatchCandidate(MatchRule.COUNTERPARTY_AMOUNT, score,
                    List.of(new MatchCandidate.Item(invoice, tx.getAmount())), reasons));
        }
        return candidates;
    }
}
