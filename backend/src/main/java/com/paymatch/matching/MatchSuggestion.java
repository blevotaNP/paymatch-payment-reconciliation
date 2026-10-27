package com.paymatch.matching;

import com.paymatch.transaction.BankTransaction;
import com.paymatch.user.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "match_suggestion")
public class MatchSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id")
    private BankTransaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchRule rule;

    private int score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Confidence confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<MatchReason> reasons = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SuggestionStatus status = SuggestionStatus.PENDING;

    @OneToMany(mappedBy = "suggestion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MatchSuggestionItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private AppUser decidedBy;

    private Instant decidedAt;

    protected MatchSuggestion() {
    }

    public MatchSuggestion(BankTransaction transaction, MatchRule rule, int score, List<MatchReason> reasons) {
        this.transaction = transaction;
        this.rule = rule;
        this.score = score;
        this.confidence = Confidence.fromScore(score);
        this.reasons = new ArrayList<>(reasons);
    }

    public void addItem(MatchSuggestionItem item) {
        items.add(item);
    }

    public void decide(SuggestionStatus status, AppUser user, Instant when) {
        this.status = status;
        this.decidedBy = user;
        this.decidedAt = when;
    }

    public Long getId() {
        return id;
    }

    public BankTransaction getTransaction() {
        return transaction;
    }

    public MatchRule getRule() {
        return rule;
    }

    public int getScore() {
        return score;
    }

    public Confidence getConfidence() {
        return confidence;
    }

    public List<MatchReason> getReasons() {
        return reasons;
    }

    public SuggestionStatus getStatus() {
        return status;
    }

    public List<MatchSuggestionItem> getItems() {
        return items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public AppUser getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
