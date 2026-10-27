package com.paymatch.review;

import com.paymatch.transaction.BankTransactionRepository;
import com.paymatch.transaction.IgnoreReason;
import com.paymatch.transaction.MatchStatus;
import com.paymatch.transaction.TransactionDetailsResponse;
import com.paymatch.transaction.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Review actions. Each returns the transaction as it is after the change; the response is read in a separate
 * transaction after the commit, so it carries the new version for the next action.
 */
@RestController
@RequestMapping("/api/review")
@Tag(name = "Review")
public class ReviewController {

    private final ReviewService reviewService;
    private final TransactionService transactionService;
    private final BankTransactionRepository transactionRepository;

    public ReviewController(ReviewService reviewService, TransactionService transactionService,
                            BankTransactionRepository transactionRepository) {
        this.reviewService = reviewService;
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
    }

    @GetMapping("/summary")
    @Operation(summary = "Counts for the review queue badge")
    public ReviewSummary summary() {
        return new ReviewSummary(
                transactionRepository.countByMatchStatus(MatchStatus.SUGGESTED),
                transactionRepository.countByMatchStatusAndAmountGreaterThan(MatchStatus.UNMATCHED, BigDecimal.ZERO));
    }

    @GetMapping("/ignore-reasons")
    public List<IgnoreReasonResponse> ignoreReasons() {
        return Arrays.stream(IgnoreReason.values()).map(r -> new IgnoreReasonResponse(r, r.getLabel())).toList();
    }

    @PostMapping("/transactions/{id}/confirm")
    @Operation(summary = "Confirm one of the suggestions")
    public TransactionDetailsResponse confirm(@PathVariable Long id, @Valid @RequestBody ReviewRequests.Confirm request) {
        reviewService.confirm(id, request);
        return transactionService.get(id);
    }

    @PostMapping("/transactions/{id}/reject")
    @Operation(summary = "Reject a suggestion; it will not be suggested again")
    public TransactionDetailsResponse reject(@PathVariable Long id, @Valid @RequestBody ReviewRequests.Reject request) {
        reviewService.reject(id, request);
        return transactionService.get(id);
    }

    @PostMapping("/transactions/{id}/allocate")
    @Operation(summary = "Match to invoices chosen by hand; several parts split the payment")
    public TransactionDetailsResponse allocate(@PathVariable Long id, @Valid @RequestBody ReviewRequests.Allocate request) {
        reviewService.allocateManually(id, request);
        return transactionService.get(id);
    }

    @PostMapping("/transactions/{id}/ignore")
    @Operation(summary = "Mark as not an invoice payment (refund, fee, own transfer...)")
    public TransactionDetailsResponse ignore(@PathVariable Long id, @Valid @RequestBody ReviewRequests.Ignore request) {
        reviewService.ignore(id, request);
        return transactionService.get(id);
    }

    @PostMapping("/transactions/{id}/undo")
    @Operation(summary = "Undo a confirmed match or an ignore decision")
    public TransactionDetailsResponse undo(@PathVariable Long id, @Valid @RequestBody ReviewRequests.Undo request) {
        reviewService.undo(id, request);
        return transactionService.get(id);
    }

    public record ReviewSummary(long toReview, long unmatched) {
    }

    public record IgnoreReasonResponse(IgnoreReason code, String label) {
    }
}
