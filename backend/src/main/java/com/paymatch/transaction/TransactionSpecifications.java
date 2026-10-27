package com.paymatch.transaction;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    static Specification<BankTransaction> matching(TransactionFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(filter.search())) {
                String pattern = "%" + filter.search().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("counterpartyName")), pattern),
                        cb.like(root.get("counterpartyAccount"), pattern),
                        cb.like(cb.lower(root.get("bankReference")), pattern)));
            }
            if (filter.direction() == TransactionFilter.Direction.INCOMING) {
                predicates.add(cb.greaterThan(root.get("amount"), BigDecimal.ZERO));
            } else if (filter.direction() == TransactionFilter.Direction.OUTGOING) {
                predicates.add(cb.lessThan(root.get("amount"), BigDecimal.ZERO));
            }
            if (filter.importId() != null) {
                predicates.add(cb.equal(root.get("statementImport").get("id"), filter.importId()));
            }
            if (filter.matchStatus() != null && !filter.matchStatus().isEmpty()) {
                predicates.add(root.get("matchStatus").in(filter.matchStatus()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("bookingDate"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("bookingDate"), filter.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
