package com.paymatch.invoice;

import com.paymatch.allocation.AllocationResponse;
import com.paymatch.common.error.BadRequestException;
import com.paymatch.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

@RestController
@RequestMapping("/api/invoices")
@Tag(name = "Invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoiceCsvImporter csvImporter;

    public InvoiceController(InvoiceService invoiceService, InvoiceCsvImporter csvImporter) {
        this.invoiceService = invoiceService;
        this.csvImporter = csvImporter;
    }

    @GetMapping
    @Operation(summary = "Search invoices by number, counterparty name or NIP")
    public PageResponse<InvoiceResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<InvoiceStatus> status,
            @RequestParam(required = false) Long counterpartyId,
            @RequestParam(required = false) Boolean overdue,
            @ParameterObject @PageableDefault(size = 20, sort = "dueDate", direction = Sort.Direction.ASC) Pageable pageable) {
        InvoiceFilter filter = new InvoiceFilter(search, status, counterpartyId, overdue);
        return PageResponse.of(invoiceService.search(filter, pageable), Function.identity());
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable Long id) {
        return invoiceService.get(id);
    }

    @GetMapping("/{id}/payments")
    @Operation(summary = "Confirmed payments (allocations) of an invoice")
    public List<AllocationResponse> payments(@PathVariable Long id) {
        return invoiceService.payments(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse create(@Valid @RequestBody InvoiceRequest request) {
        return invoiceService.create(request);
    }

    @PutMapping("/{id}")
    public InvoiceResponse update(@PathVariable Long id, @Valid @RequestBody InvoiceRequest request) {
        return invoiceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        invoiceService.delete(id);
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk import invoices from CSV; invalid rows are skipped and reported")
    public InvoiceImportResult importCsv(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        return csvImporter.importCsv(file.getBytes());
    }
}
