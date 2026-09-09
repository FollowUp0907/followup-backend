package com.followup.ai.controller;

import com.followup.ai.dto.AnalysisConfirmReqDto;
import com.followup.ai.dto.AnalysisResDto;
import com.followup.ai.service.AiAnalysisService;
import com.followup.ai.service.AiAnalysisService.AnalysisRequestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI Analysis")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    @Operation(summary = "AI 분석 요청")
    @ApiResponse(responseCode = "200", description = "재사용 가능한 기존 분석을 그대로 반환")
    @ApiResponse(responseCode = "201", description = "새 분석을 생성해 반환")
    @PostMapping("/meeting/{meetingId}/analysis")
    public ResponseEntity<AnalysisResDto> requestAnalysis(@PathVariable Long meetingId) {
        AnalysisRequestResult result = aiAnalysisService.requestAnalysis(meetingId);
        HttpStatus status = result.reused() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.response());
    }

    @Operation(summary = "AI 분석 결과 조회")
    @GetMapping("/analysis/{analysisId}")
    public ResponseEntity<AnalysisResDto> getAnalysis(@PathVariable Long analysisId) {
        return ResponseEntity.ok(aiAnalysisService.getAnalysis(analysisId));
    }

    @Operation(summary = "AI 분석 결과 확정")
    @ApiResponse(responseCode = "200", description = "확정 성공")
    @ApiResponse(responseCode = "409", description = "이미 확정되었거나 확정 가능한 상태가 아님")
    @PostMapping("/analysis/{analysisId}/confirm")
    public ResponseEntity<AnalysisResDto> confirmAnalysis(@PathVariable Long analysisId,
                                                             @Valid @RequestBody AnalysisConfirmReqDto request) {
        return ResponseEntity.ok(aiAnalysisService.confirmAnalysis(analysisId, request));
    }
}
