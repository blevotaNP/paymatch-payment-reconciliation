package com.paymatch.transaction;

public record TransactionDetailsResponse(TransactionResponse transaction, String importFileName, String rawData) {
}
