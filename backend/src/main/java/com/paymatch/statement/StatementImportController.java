package com.paymatch.statement;

import com.paymatch.common.error.BadRequestException;
import com.paymatch.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

@RestController
@RequestMapping("/api/imports")
@Tag(name = "Statement imports")
public class StatementImportController {

    private final StatementImportService importService;

    public StatementImportController(StatementImportService importService) {
        this.importService = importService;
    }

    @GetMapping("/formats")
    @Operation(summary = "Supported bank statement formats")
    public List<BankFormatResponse> formats() {
        return Arrays.stream(BankFormat.values()).map(BankFormatResponse::from).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a bank statement; the format is detected automatically unless given")
    public StatementImportResponse upload(@RequestPart("file") MultipartFile file,
                                          @RequestParam(required = false) BankFormat format) throws IOException {
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "statement";
        return importService.importFile(fileName, file.getBytes(), format);
    }

    @GetMapping
    public PageResponse<StatementImportResponse> list(
            @ParameterObject @PageableDefault(size = 20, sort = "importedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(importService.list(pageable), Function.identity());
    }

    @GetMapping("/{id}")
    public StatementImportResponse get(@PathVariable Long id) {
        return importService.get(id);
    }

    @PostMapping("/{id}/undo")
    @Operation(summary = "Remove all transactions of this import; the import stays in history as undone")
    public StatementImportResponse undo(@PathVariable Long id) {
        return importService.undo(id);
    }
}
