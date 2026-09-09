package com.followup.dashboard.controller;

import com.followup.dashboard.dto.DashboardResDto;
import com.followup.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "대시보드 조회")
    @GetMapping("/project/{projectId}/dashboard")
    public ResponseEntity<DashboardResDto> getDashboard(@PathVariable Long projectId) {
        return ResponseEntity.ok(dashboardService.getDashboard(projectId));
    }
}
