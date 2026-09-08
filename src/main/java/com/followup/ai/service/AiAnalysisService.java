package com.followup.ai.service;

import com.followup.ai.client.AiAnalysisClient;
import com.followup.ai.dto.AiDraftResult;
import com.followup.ai.dto.AnalysisResponse;
import com.followup.ai.entity.AiAnalysisRun;
import com.followup.ai.repository.AiAnalysisRunRepository;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.project.repository.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 외부 Gemini 호출이 열린 DB 트랜잭션 안에서 실행되지 않도록, DB 작업은
 * {@link AiAnalysisRunTransactionService}의 짧은 트랜잭션으로 분리해 처리한다.
 * 재사용 가능한 이전 분석이 있으면 Gemini를 다시 호출하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final AiAnalysisRunRepository aiAnalysisRunRepository;
    private final AiAnalysisClient aiAnalysisClient;
    private final AiAnalysisRunTransactionService aiAnalysisRunTransactionService;
    private final ProjectMemberRepository projectMemberRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    public AnalysisRequestResult requestAnalysis(Long meetingId) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        String modelName = aiAnalysisClient.getModelName();
        String promptVersion = aiAnalysisClient.getPromptVersion();

        AiAnalysisRunTransactionService.AnalysisStart start =
                aiAnalysisRunTransactionService.startAnalysis(meetingId, currentUserId, modelName, promptVersion);

        if (!start.reused()) {
            try {
                AiDraftResult draft = aiAnalysisClient.analyze(start.meetingContent(), start.meetingScheduledAt());
                aiAnalysisRunTransactionService.completeWithSuccess(start.analysisId(), writeJson(draft));
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : ErrorCode.AI_ANALYSIS_FAILED.getMessage();
                aiAnalysisRunTransactionService.completeWithFailure(start.analysisId(), message);
            }
        }

        return new AnalysisRequestResult(getAnalysis(start.analysisId()), start.reused());
    }

    @Transactional(readOnly = true)
    public AnalysisResponse getAnalysis(Long analysisId) {
        AiAnalysisRun run = getAnalysisOrThrow(analysisId);
        requireMember(run.getMeeting().getProject().getId(), currentUserProvider.getCurrentUserId());

        return AnalysisResponse.of(run, readJson(run.getDraftJson()));
    }

    private String writeJson(AiDraftResult draft) {
        return objectMapper.writeValueAsString(draft);
    }

    private AiDraftResult readJson(String draftJson) {
        return draftJson != null ? objectMapper.readValue(draftJson, AiDraftResult.class) : null;
    }

    private AiAnalysisRun getAnalysisOrThrow(Long analysisId) {
        return aiAnalysisRunRepository.findById(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_NOT_FOUND));
    }

    private void requireMember(Long projectId, Long userId) {
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
    }

    /** 200(재사용)과 201(신규 생성)을 구분하기 위한 내부 결과 타입이다. */
    public record AnalysisRequestResult(AnalysisResponse response, boolean reused) {
    }
}
