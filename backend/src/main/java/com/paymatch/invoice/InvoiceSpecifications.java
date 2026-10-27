package com.paymatch.invoice;

import com.paymatch.counterparty.Counterparty;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class InvoiceSpecifications {

    private InvoiceSpecifications() {
    }

    static Specification<Invoice> matching(InvoiceFilter filter, LocalDate today) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(filter.search())) {
                String pattern = "%" + filter.search().trim().toLowerCase() + "%";
                Join<Invoice, Counterparty> counterparty = root.join("counterparty");
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("number")), pattern),
                        cb.like(cb.lower(counterparty.get("name")), pattern),
                        cb.like(counterparty.get("nip"), pattern)));
            }
            if (filter.status() != null && !filter.status().isEmpty()) {
                predicates.add(root.get("status").in(filter.status()));
            }
            if (filter.counterpartyId() != null) {
                predicates.add(cb.equal(root.get("counterparty").get("id"), filter.counterpartyId()));
            }
            if (Boolean.TRUE.equals(filter.overdue())) {
                predicates.add(cb.lessThan(root.get("dueDate"), today));
                predicates.add(root.get("status").in(InvoiceStatus.UNPAID, InvoiceStatus.PARTIALLY_PAID));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
