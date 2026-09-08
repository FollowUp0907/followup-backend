package com.followup.ai.dto;

import com.followup.ai.entity.AiAnalysisRun;
import com.followup.ai.entity.AnalysisStatus;
import java.time.LocalDateTime;

public record AnalysisResponse(
        Long id,
        Long meetingId,
        AnalysisStatus status,
        String modelName,
        String promptVersion,
        AiDraftResult draft,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt
) {

    public static AnalysisResponse of(AiAnalysisRun run, AiDraftResult draft) {
        return new AnalysisResponse(
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
