package com.paymatch.counterparty;

import com.paymatch.common.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/counterparties")
@Tag(name = "Counterparties")
public class CounterpartyController {

    private final CounterpartyService counterpartyService;

    public CounterpartyController(CounterpartyService counterpartyService) {
        this.counterpartyService = counterpartyService;
    }

    @GetMapping
    public PageResponse<CounterpartyResponse> list(
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(counterpartyService.search(search, pageable), CounterpartyResponse::from);
    }

    @GetMapping("/{id}")
    public CounterpartyResponse get(@PathVariable Long id) {
        return CounterpartyResponse.from(counterpartyService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CounterpartyResponse create(@Valid @RequestBody CounterpartyRequest request) {
        return CounterpartyResponse.from(counterpartyService.create(request));
    }

    @PutMapping("/{id}")
    public CounterpartyResponse update(@PathVariable Long id, @Valid @RequestBody CounterpartyRequest request) {
        return CounterpartyResponse.from(counterpartyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        counterpartyService.delete(id);
    }
}
