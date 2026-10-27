package com.paymatch.invoice;

import java.util.List;

public record InvoiceFilter(String search, List<InvoiceStatus> status, Long counterpartyId, Boolean overdue) {
}
