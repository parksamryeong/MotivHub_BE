package com.motivhub.be.dashboard.controller;

import com.motivhub.be.dashboard.dto.DashboardStatsResponse;
import com.motivhub.be.dashboard.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/dashboard/stats")
    public ResponseEntity<DashboardStatsResponse> getStats(
            @AuthenticationPrincipal Long userId, @RequestParam(required = false) Long workspaceId) {
        return ResponseEntity.ok(dashboardService.getStats(userId, workspaceId));
    }
}
