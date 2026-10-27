package com.paymatch.matching;

import com.paymatch.invoice.Invoice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "match_suggestion_item")
public class MatchSuggestionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "suggestion_id")
    private MatchSuggestion suggestion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    protected MatchSuggestionItem() {
    }

    public MatchSuggestionItem(MatchSuggestion suggestion, Invoice invoice, BigDecimal amount) {
        this.suggestion = suggestion;
        this.invoice = invoice;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public MatchSuggestion getSuggestion() {
        return suggestion;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
