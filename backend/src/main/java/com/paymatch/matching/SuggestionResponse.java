package com.paymatch.matching;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SuggestionResponse(
        Long id,
        MatchRule rule,
        String ruleLabel,
        String ruleDescription,
        int score,
        Confidence confidence,
        SuggestionStatus status,
        List<MatchReason> reasons,
        List<Item> items,
        String decidedBy,
        Instant decidedAt) {

    public static SuggestionResponse from(MatchSuggestion s) {
        return new SuggestionResponse(
                s.getId(),
                s.getRule(),
                s.getRule().getLabel(),
                s.getRule().getDescription(),
                s.getScore(),
                s.getConfidence(),
                s.getStatus(),
                s.getReasons(),
                s.getItems().stream().map(item -> new Item(
                        item.getInvoice().getId(),
                        item.getInvoice().getNumber(),
                        item.getInvoice().getCounterparty().getName(),
                        item.getInvoice().getDueDate(),
                        item.getInvoice().getGrossAmount(),
                        item.getInvoice().getRemainingAmount(),
                        item.getAmount())).toList(),
                s.getDecidedBy() != null ? s.getDecidedBy().getDisplayName() : null,
                s.getDecidedAt());
    }

    public record Item(Long invoiceId, String invoiceNumber, String counterpartyName, LocalDate dueDate,
                       BigDecimal grossAmount, BigDecimal remainingAmount, BigDecimal amount) {
    }
}
