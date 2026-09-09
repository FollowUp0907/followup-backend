package com.followup.global.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health")
@RestController
public class HealthController {

    @Operation(summary = "서버 상태 확인")
    @SecurityRequirements
    @GetMapping("/api/health")
    public HealthResponse health() {
        return HealthResponse.ok();
    }
}
