package com.paymatch.invoice;

import com.paymatch.common.error.ConflictException;
import com.paymatch.common.error.NotFoundException;
import com.paymatch.counterparty.Counterparty;
import com.paymatch.counterparty.CounterpartyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final CounterpartyService counterpartyService;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoiceRepository, CounterpartyService counterpartyService, Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.counterpartyService = counterpartyService;
        this.clock = clock;
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
        Invoice invoice = new Invoice(number, counterparty, request.issueDate(), request.dueDate(),
                request.grossAmount(), request.currencyOrDefault());
        return InvoiceResponse.from(invoiceRepository.save(invoice), today());
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
        invoice.updateDetails(number, counterparty, request.issueDate(), request.dueDate(),
                request.grossAmount(), request.currencyOrDefault());
        // flush so the response carries the incremented version
        invoiceRepository.flush();
        return InvoiceResponse.from(invoice, today());
    }

    @Transactional
    public void delete(Long id) {
        Invoice invoice = findInvoice(id);
        if (invoice.getPaidAmount().signum() > 0) {
            throw new ConflictException("Invoice has matched payments; undo them before deleting");
        }
        invoiceRepository.delete(invoice);
    }

    private Invoice findInvoice(Long id) {
        return invoiceRepository.findById(id).orElseThrow(() -> NotFoundException.of("Invoice", id));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
