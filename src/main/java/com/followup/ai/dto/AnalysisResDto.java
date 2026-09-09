package com.followup.ai.dto;

import com.followup.ai.entity.AiAnalysisRun;
import com.followup.ai.entity.AnalysisStatus;
import java.time.LocalDateTime;

public record AnalysisResDto(
        Long id,
        Long meetingId,
        AnalysisStatus status,
        String modelName,
        String promptVersion,
        AiDraftResultDto draft,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt
) {

    public static AnalysisResDto of(AiAnalysisRun run, AiDraftResultDto draft) {
        return new AnalysisResDto(
                run.getId(),
                run.getMeeting().getId(),
                run.getStatus(),
                run.getModelName(),
                run.getPromptVersion(),
                draft,
                run.getErrorMessage(),
                run.getCreatedAt(),
                run.getConfirmedAt()
        );
    }
}
