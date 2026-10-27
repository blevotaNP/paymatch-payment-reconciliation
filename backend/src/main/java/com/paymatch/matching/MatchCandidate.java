package com.paymatch.matching;

import java.math.BigDecimal;
import java.util.List;

/** A suggestion produced by a rule, before it is stored. */
public record MatchCandidate(MatchRule rule, int score, List<Item> items, List<MatchReason> reasons) {

    public MatchCandidate {
        score = Math.max(0, Math.min(100, score));
        items = List.copyOf(items);
        reasons = List.copyOf(reasons);
    }

    public Confidence confidence() {
        return Confidence.fromScore(score);
    }

    /** Invoice ids in a stable order, used to recognise two candidates that point at the same invoices. */
    public String invoiceKey() {
        return items.stream().map(item -> item.invoice().getId()).sorted().map(String::valueOf)
                .reduce((a, b) -> a + "," + b).orElse("");
    }

    public BigDecimal totalAmount() {
        return items.stream().map(Item::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record Item(InvoiceSnapshot invoice, BigDecimal amount) {
    }
}
