package com.paymatch.matching;

import com.paymatch.matching.rule.MatchingRule;
import com.paymatch.transaction.BankTransaction;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Runs all rules for one transaction and turns their output into a short, ranked list of suggestions. */
@Component
public class MatchingEngine {

    static final int MAX_SUGGESTIONS = 5;
    /** Only a perfect exact match (number written as on the invoice + exact amount) is confirmed without review. */
    static final int AUTO_CONFIRM_SCORE = 95;

    private final List<MatchingRule> rules;

    public MatchingEngine(List<MatchingRule> rules) {
        this.rules = rules;
    }

    /**
     * @param rejectedKeys invoice keys a person already rejected for this transaction; they are not suggested again
     */
    public List<MatchCandidate> suggest(BankTransaction tx, MatchingContext context, Set<String> rejectedKeys) {
        if (!tx.isIncoming()) {
            return List.of();
        }
        // several rules can point at the same invoices (number rule and counterparty rule); keep the best explanation
        Map<String, MatchCandidate> best = new LinkedHashMap<>();
        for (MatchingRule rule : rules) {
            for (MatchCandidate candidate : rule.apply(tx, context)) {
                if (rejectedKeys.contains(candidate.invoiceKey())) {
                    continue;
                }
                best.merge(candidate.invoiceKey(), candidate, (a, b) -> b.score() > a.score() ? b : a);
            }
        }
        return best.values().stream()
                .sorted(Comparator.comparingInt(MatchCandidate::score).reversed())
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    /** The top candidate, if it is good enough and no other strong candidate competes with it. */
    public Optional<MatchCandidate> autoConfirmable(List<MatchCandidate> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        MatchCandidate top = candidates.getFirst();
        boolean qualifies = top.rule() == MatchRule.EXACT && top.score() >= AUTO_CONFIRM_SCORE;
        boolean competitor = candidates.stream().skip(1).anyMatch(other -> other.confidence() == Confidence.HIGH);
        return qualifies && !competitor ? Optional.of(top) : Optional.empty();
    }
}
