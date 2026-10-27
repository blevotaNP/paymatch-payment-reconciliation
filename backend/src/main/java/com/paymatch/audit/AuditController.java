package com.paymatch.audit;

import com.paymatch.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit log")
public class AuditController {

    private final AuditEventRepository repository;

    public AuditController(AuditEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Activity log, newest first")
    public PageResponse<AuditEventResponse> list(@RequestParam(required = false) List<AuditAction> action,
                                                 @ParameterObject @PageableDefault(size = 30) Pageable pageable) {
        Page<AuditEvent> page = action == null || action.isEmpty()
                ? repository.findAllByOrderByOccurredAtDescIdDesc(pageable)
                : repository.findByActionInOrderByOccurredAtDescIdDesc(action, pageable);
        return PageResponse.of(page, AuditEventResponse::from);
    }

    @GetMapping("/transactions/{id}")
    @Transactional(readOnly = true)
    public List<AuditEventResponse> forTransaction(@PathVariable Long id) {
        return repository.findByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc(AuditEntityType.TRANSACTION, id).stream()
                .map(AuditEventResponse::from).toList();
    }

    @GetMapping("/invoices/{id}")
    @Transactional(readOnly = true)
    public List<AuditEventResponse> forInvoice(@PathVariable Long id) {
        return repository.findForInvoice(id).stream().map(AuditEventResponse::from).toList();
    }
}
