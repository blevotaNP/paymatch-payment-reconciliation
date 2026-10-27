package com.paymatch.report;

import com.paymatch.report.DashboardResponse.CounterpartyOverdue;
import com.paymatch.report.DashboardResponse.CountAndAmount;
import com.paymatch.report.DashboardResponse.MatchingStats;
import com.paymatch.report.DashboardResponse.MonthPoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/** Read-only aggregate queries for the dashboard. Plain SQL is easier to read here than JPQL. */
@Repository
public class DashboardRepository {

    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CountAndAmount unmatchedPayments() {
        return jdbc.queryForObject("""
                select count(*), coalesce(sum(amount), 0)
                from bank_transaction
                where amount > 0 and currency = 'PLN' and match_status in ('UNMATCHED', 'SUGGESTED')
                """, (rs, i) -> new CountAndAmount(rs.getLong(1), rs.getBigDecimal(2)));
    }

    public CountAndAmount openInvoices(LocalDate dueBefore) {
        return jdbc.queryForObject("""
                select count(*), coalesce(sum(gross_amount - paid_amount), 0)
                from invoice
                where status in ('UNPAID', 'PARTIALLY_PAID') and currency = 'PLN'
                  and (?::date is null or due_date < ?::date)
                """, (rs, i) -> new CountAndAmount(rs.getLong(1), rs.getBigDecimal(2)),
                toSqlDate(dueBefore), toSqlDate(dueBefore));
    }

    public MatchingStats matchingStats() {
        return jdbc.queryForObject("""
                select count(*),
                       count(*) filter (where t.match_status = 'MATCHED' and a.has_manual is false),
                       count(*) filter (where t.match_status = 'MATCHED' and a.has_manual is true),
                       count(*) filter (where t.match_status = 'IGNORED'),
                       count(*) filter (where t.match_status in ('UNMATCHED', 'SUGGESTED'))
                from bank_transaction t
                left join (select transaction_id, bool_or(source = 'MANUAL') as has_manual
                           from allocation group by transaction_id) a on a.transaction_id = t.id
                where t.amount > 0
                """, (rs, i) -> new MatchingStats(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)));
    }

    /** Invoiced (by issue date) and received (by booking date) per month, including months with nothing. */
    public List<MonthPoint> months(LocalDate firstMonth, LocalDate lastMonth) {
        return jdbc.query("""
                select to_char(m.month, 'YYYY-MM'),
                       coalesce((select sum(gross_amount) from invoice
                                 where currency = 'PLN' and date_trunc('month', issue_date) = m.month), 0),
                       coalesce((select sum(amount) from bank_transaction
                                 where amount > 0 and currency = 'PLN' and match_status <> 'IGNORED'
                                   and date_trunc('month', booking_date) = m.month), 0)
                from generate_series(?::date, ?::date, interval '1 month') as m(month)
                order by m.month
                """, (rs, i) -> new MonthPoint(rs.getString(1), rs.getBigDecimal(2), rs.getBigDecimal(3)),
                Date.valueOf(firstMonth), Date.valueOf(lastMonth));
    }

    public List<CounterpartyOverdue> topOverdue(LocalDate today, int limit) {
        return jdbc.query("""
                select c.id, c.name, count(*), sum(i.gross_amount - i.paid_amount), max(?::date - i.due_date)
                from invoice i join counterparty c on c.id = i.counterparty_id
                where i.status in ('UNPAID', 'PARTIALLY_PAID') and i.currency = 'PLN' and i.due_date < ?::date
                group by c.id, c.name
                order by sum(i.gross_amount - i.paid_amount) desc
                limit ?
                """, (rs, i) -> new CounterpartyOverdue(rs.getLong(1), rs.getString(2), rs.getLong(3),
                        rs.getBigDecimal(4), rs.getLong(5)),
                Date.valueOf(today), Date.valueOf(today), limit);
    }

    private static Date toSqlDate(LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }
}
