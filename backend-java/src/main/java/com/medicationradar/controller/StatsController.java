package com.medicationradar.controller;

import com.medicationradar.security.AuthUser;
import com.medicationradar.service.ReportService;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {
    private final ReportService reportService;

    public StatsController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public Map<String, Object> stats(@AuthenticationPrincipal AuthUser user) {
        if (user.isAdmin()) {
            return reportService.globalStats();
        }
        return reportService.stats(user.id());
    }
}
