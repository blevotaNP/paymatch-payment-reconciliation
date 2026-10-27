package com.paymatch.report;

import com.paymatch.audit.AuditEventResponse;

import java.math.BigDecimal;
import java.util.List;

/** Amounts are in PLN; invoices and payments in other currencies are left out of the sums. */
public record DashboardResponse(
        long toReview,
        CountAndAmount unmatchedPayments,
        CountAndAmount openInvoices,
        CountAndAmount overdueInvoices,
        MatchingStats matching,
        List<MonthPoint> months,
        List<CounterpartyOverdue> topOverdue,
        List<AuditEventResponse> recentActivity) {

    public record CountAndAmount(long count, BigDecimal amount) {
    }

    /**
     * @param autoMatched  incoming payments matched without anyone looking
     * @param manualMatched incoming payments matched by a person
     * @param ignored      marked as not an invoice payment
     */
    public record MatchingStats(long incoming, long autoMatched, long manualMatched, long ignored, long open) {
    }

    public record MonthPoint(String month, BigDecimal invoiced, BigDecimal received) {
    }

    public record CounterpartyOverdue(Long counterpartyId, String name, long invoices, BigDecimal amount, long maxDaysOverdue) {
    }
}
