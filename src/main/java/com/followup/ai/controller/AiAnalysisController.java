package com.followup.ai.controller;

import com.followup.ai.dto.AnalysisResponse;
import com.followup.ai.service.AiAnalysisService;
import com.followup.ai.service.AiAnalysisService.AnalysisRequestResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    @PostMapping("/meeting/{meetingId}/analysis")
    public ResponseEntity<AnalysisResponse> requestAnalysis(@PathVariable Long meetingId) {
        AnalysisRequestResult result = aiAnalysisService.requestAnalysis(meetingId);
        HttpStatus status = result.reused() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.response());
    }

    @GetMapping("/analysis/{analysisId}")
    public ResponseEntity<AnalysisResponse> getAnalysis(@PathVariable Long analysisId) {
        return ResponseEntity.ok(aiAnalysisService.getAnalysis(analysisId));
    }
}
