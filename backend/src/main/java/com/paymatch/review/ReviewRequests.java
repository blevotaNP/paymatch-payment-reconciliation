package com.paymatch.review;

import com.paymatch.transaction.IgnoreReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request bodies of the review actions. Each one carries the transaction version the person was looking at,
 * so a decision made on an outdated screen is refused instead of silently overwriting someone else's.
 */
public final class ReviewRequests {

    private ReviewRequests() {
    }

    public record Confirm(@NotNull Long suggestionId, @NotNull Long version) {
    }

    public record Reject(@NotNull Long suggestionId, @NotNull Long version) {
    }

    public record Allocate(@NotEmpty @Size(max = 20) List<@Valid Part> parts, @Size(max = 500) String note, @NotNull Long version) {
    }

    public record Part(@NotNull Long invoiceId, @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount) {
    }

    public record Ignore(@NotNull IgnoreReason reason, @Size(max = 500) String note, @NotNull Long version) {
    }

    public record Undo(@NotNull Long version) {
    }
}
