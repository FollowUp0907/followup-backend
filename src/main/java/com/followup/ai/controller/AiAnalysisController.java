package com.followup.ai.controller;

import com.followup.ai.dto.AnalysisConfirmReqDto;
import com.followup.ai.dto.AnalysisResDto;
import com.followup.ai.service.AiAnalysisService;
import com.followup.ai.service.AiAnalysisService.AnalysisRequestResult;
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

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    @PostMapping("/meeting/{meetingId}/analysis")
    public ResponseEntity<AnalysisResDto> requestAnalysis(@PathVariable Long meetingId) {
        AnalysisRequestResult result = aiAnalysisService.requestAnalysis(meetingId);
        HttpStatus status = result.reused() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.response());
    }

    @GetMapping("/analysis/{analysisId}")
    public ResponseEntity<AnalysisResDto> getAnalysis(@PathVariable Long analysisId) {
        return ResponseEntity.ok(aiAnalysisService.getAnalysis(analysisId));
    }

    @PostMapping("/analysis/{analysisId}/confirm")
    public ResponseEntity<AnalysisResDto> confirmAnalysis(@PathVariable Long analysisId,
                                                             @Valid @RequestBody AnalysisConfirmReqDto request) {
        return ResponseEntity.ok(aiAnalysisService.confirmAnalysis(analysisId, request));
    }
}
