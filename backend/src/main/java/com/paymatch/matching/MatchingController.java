package com.paymatch.matching;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/matching")
@Tag(name = "Matching")
public class MatchingController {

    private final MatchingService matchingService;

    public MatchingController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @PostMapping("/run")
    @Operation(summary = "Re-run matching for all incoming transactions that are not decided yet")
    public MatchingRunResult run() {
        return matchingService.rematchOpenTransactions();
    }

    @GetMapping("/rules")
    @Operation(summary = "Matching rules with a short explanation, for the UI legend")
    public List<RuleResponse> rules() {
        return Arrays.stream(MatchRule.values()).map(r -> new RuleResponse(r, r.getLabel(), r.getDescription())).toList();
    }

    public record RuleResponse(MatchRule code, String label, String description) {
    }
}
