package com.paymatch.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Reports")
public class ReportController {

    private final DashboardService dashboardService;
    private final AgingReportService agingReportService;

    public ReportController(DashboardService dashboardService, AgingReportService agingReportService) {
        this.dashboardService = dashboardService;
        this.agingReportService = agingReportService;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Key numbers for the start page (PLN)")
    public DashboardResponse dashboard() {
        return dashboardService.get();
    }

    @GetMapping("/reports/aging")
    @Operation(summary = "Receivables aging as of today, per counterparty")
    public AgingReport aging(@RequestParam(defaultValue = "PLN") String currency) {
        return agingReportService.build(currency);
    }
}
