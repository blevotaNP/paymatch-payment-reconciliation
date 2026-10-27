package com.paymatch.report;

import com.paymatch.audit.AuditEventRepository;
import com.paymatch.audit.AuditEventResponse;
import com.paymatch.transaction.BankTransactionRepository;
import com.paymatch.transaction.MatchStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class DashboardService {

    private static final int MONTHS = 6;

    private final DashboardRepository dashboardRepository;
    private final BankTransactionRepository transactionRepository;
    private final AuditEventRepository auditRepository;
    private final Clock clock;

    public DashboardService(DashboardRepository dashboardRepository, BankTransactionRepository transactionRepository,
                            AuditEventRepository auditRepository, Clock clock) {
        this.dashboardRepository = dashboardRepository;
        this.transactionRepository = transactionRepository;
        this.auditRepository = auditRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse get() {
        LocalDate today = LocalDate.now(clock);
        LocalDate thisMonth = today.withDayOfMonth(1);
        return new DashboardResponse(
                transactionRepository.countByMatchStatus(MatchStatus.SUGGESTED),
                dashboardRepository.unmatchedPayments(),
                dashboardRepository.openInvoices(null),
                dashboardRepository.openInvoices(today),
                dashboardRepository.matchingStats(),
                dashboardRepository.months(thisMonth.minusMonths(MONTHS - 1), thisMonth),
                dashboardRepository.topOverdue(today, 5),
                auditRepository.findAllByOrderByOccurredAtDescIdDesc(PageRequest.of(0, 6)).stream()
                        .map(AuditEventResponse::from).toList());
    }
}
