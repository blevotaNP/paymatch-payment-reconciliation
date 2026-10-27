package com.paymatch.invoice;

import com.paymatch.allocation.AllocationRepository;
import com.paymatch.allocation.AllocationResponse;
import com.paymatch.audit.AuditAction;
import com.paymatch.audit.AuditEntityType;
import com.paymatch.audit.AuditService;
import com.paymatch.common.Money;
import com.paymatch.common.error.ConflictException;
import com.paymatch.common.error.NotFoundException;
import com.paymatch.counterparty.Counterparty;
import com.paymatch.counterparty.CounterpartyService;
import com.paymatch.matching.MatchSuggestionRepository;
import com.paymatch.matching.MatchingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final CounterpartyService counterpartyService;
    private final Clock clock;
    private final MatchingService matchingService;
    private final MatchSuggestionRepository suggestionRepository;
    private final AuditService auditService;
    private final AllocationRepository allocationRepository;

    public InvoiceService(InvoiceRepository invoiceRepository, CounterpartyService counterpartyService, Clock clock,
                          MatchingService matchingService, MatchSuggestionRepository suggestionRepository,
                          AuditService auditService, AllocationRepository allocationRepository) {
        this.invoiceRepository = invoiceRepository;
        this.counterpartyService = counterpartyService;
        this.clock = clock;
        this.matchingService = matchingService;
        this.suggestionRepository = suggestionRepository;
        this.auditService = auditService;
        this.allocationRepository = allocationRepository;
    }

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> search(InvoiceFilter filter, Pageable pageable) {
        LocalDate today = today();
        return invoiceRepository.findAll(InvoiceSpecifications.matching(filter, today), pageable)
                .map(invoice -> InvoiceResponse.from(invoice, today));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(Long id) {
        return InvoiceResponse.from(findInvoice(id), today());
    }

    @Transactional
    public InvoiceResponse create(InvoiceRequest request) {
        String number = request.number().trim();
        if (invoiceRepository.existsByNumberIgnoreCase(number)) {
            throw new ConflictException("Invoice " + number + " already exists");
        }
        Counterparty counterparty = counterpartyService.get(request.counterpartyId());
        Invoice invoice = invoiceRepository.saveAndFlush(new Invoice(number, counterparty, request.issueDate(),
                request.dueDate(), request.grossAmount(), request.currencyOrDefault()));
        auditService.record(AuditAction.INVOICE_CREATED, AuditEntityType.INVOICE, invoice.getId(),
                "Created invoice " + invoice.getNumber() + " for " + Money.format(invoice.getGrossAmount(), invoice.getCurrency()),
                List.of(invoice.getId()), Map.of());
        // a payment for this invoice may already be waiting among imported transactions
        matchingService.rematchOpenTransactions();
        return InvoiceResponse.from(invoice, today());
    }

    @Transactional
    public InvoiceResponse update(Long id, InvoiceRequest request) {
        Invoice invoice = findInvoice(id);
        String number = request.number().trim();
        if (invoiceRepository.existsByNumberIgnoreCaseAndIdNot(number, id)) {
            throw new ConflictException("Invoice " + number + " already exists");
        }
        if (invoice.getPaidAmount().signum() > 0 && !invoice.getCurrency().equals(request.currencyOrDefault())) {
            throw new ConflictException("Currency cannot be changed after payments were matched");
        }
        Counterparty counterparty = counterpartyService.get(request.counterpartyId());
        String before = invoice.getNumber() + ", " + Money.format(invoice.getGrossAmount(), invoice.getCurrency())
                + ", due " + invoice.getDueDate();
        invoice.updateDetails(number, counterparty, request.issueDate(), request.dueDate(),
                request.grossAmount(), request.currencyOrDefault());
        // flush so the response carries the incremented version
        invoiceRepository.flush();
        auditService.record(AuditAction.INVOICE_UPDATED, AuditEntityType.INVOICE, invoice.getId(),
                "Updated invoice " + invoice.getNumber(), List.of(invoice.getId()),
                Map.of("before", before, "after", invoice.getNumber() + ", "
                        + Money.format(invoice.getGrossAmount(), invoice.getCurrency()) + ", due " + invoice.getDueDate()));
        matchingService.rematchOpenTransactions();
        return InvoiceResponse.from(invoice, today());
    }

    @Transactional
    public void delete(Long id) {
        Invoice invoice = findInvoice(id);
        if (invoice.getPaidAmount().signum() > 0) {
            throw new ConflictException("Invoice has matched payments; undo them before deleting");
        }
        List<Long> affectedTransactions = suggestionRepository.findTransactionIdsWithPendingSuggestionFor(id);
        auditService.record(AuditAction.INVOICE_DELETED, AuditEntityType.INVOICE, id,
                "Deleted invoice " + invoice.getNumber(), List.of(), Map.of());
        invoiceRepository.delete(invoice);
        invoiceRepository.flush();
        matchingService.rematch(affectedTransactions);
    }

    @Transactional(readOnly = true)
    public List<AllocationResponse> payments(Long id) {
        findInvoice(id);
        return allocationRepository.findByInvoiceIdOrderByCreatedAt(id).stream().map(AllocationResponse::from).toList();
    }

    private Invoice findInvoice(Long id) {
        return invoiceRepository.findById(id).orElseThrow(() -> NotFoundException.of("Invoice", id));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
