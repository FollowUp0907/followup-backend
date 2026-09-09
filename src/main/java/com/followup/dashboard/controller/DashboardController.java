package com.followup.dashboard.controller;

import com.followup.dashboard.dto.DashboardResDto;
import com.followup.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/project/{projectId}/dashboard")
    public ResponseEntity<DashboardResDto> getDashboard(@PathVariable Long projectId) {
        return ResponseEntity.ok(dashboardService.getDashboard(projectId));
    }
}
