package com.paymatch.transaction;

import com.paymatch.common.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Bank transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public PageResponse<TransactionResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TransactionFilter.Direction direction,
            @RequestParam(required = false) Long importId,
            @RequestParam(required = false) List<MatchStatus> matchStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = 20, sort = {"bookingDate", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        TransactionFilter filter = new TransactionFilter(search, direction, importId, from, to, matchStatus);
        return PageResponse.of(transactionService.search(filter, pageable), Function.identity());
    }

    @GetMapping("/{id}")
    public TransactionDetailsResponse get(@PathVariable Long id) {
        return transactionService.get(id);
    }
}
