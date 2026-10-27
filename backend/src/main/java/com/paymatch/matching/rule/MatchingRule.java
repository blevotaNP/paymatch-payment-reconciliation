package com.paymatch.matching.rule;

import com.paymatch.matching.MatchCandidate;
import com.paymatch.matching.MatchingContext;
import com.paymatch.transaction.BankTransaction;

import java.util.List;

/** One way of finding invoices for an incoming transfer. Rules are independent; the engine merges their results. */
public interface MatchingRule {

    List<MatchCandidate> apply(BankTransaction transaction, MatchingContext context);
}
