package com.paymatch.transaction;

import com.paymatch.allocation.AllocationResponse;
import com.paymatch.matching.SuggestionResponse;

import java.util.List;

public record TransactionDetailsResponse(
        TransactionResponse transaction,
        String importFileName,
        String rawData,
        List<SuggestionResponse> suggestions,
        List<AllocationResponse> allocations) {
}
