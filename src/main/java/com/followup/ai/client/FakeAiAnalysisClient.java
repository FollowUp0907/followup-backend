package com.followup.ai.client;

import com.followup.actionitem.entity.Priority;
import com.followup.ai.dto.AiDraftResultDto;
import com.followup.ai.dto.AiDraftResultDto.DraftActionItem;
import com.followup.ai.dto.AiDraftResultDto.DraftDecision;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// GEMINI_API_KEY가 없을 때 사용되는 fallback 구현체로, 입력과 무관하게 고정된 draft를 반환한다.
public class FakeAiAnalysisClient implements AiAnalysisClient {

    @Override
    public AiDraftResultDto analyze(String meetingContent, LocalDateTime meetingScheduledAt) {
        return new AiDraftResultDto(
                List.of(new DraftDecision("Placeholder decision generated from meeting notes")),
                List.of(new DraftActionItem(
                        "Follow up on meeting discussion",
                        "Auto-generated placeholder action item",
                        null,
                        LocalDate.now().plusDays(7),
                        Priority.MEDIUM,
                        "Default priority assigned by fake AI client"
                ))
        );
    }

    @Override
    public String getModelName() {
        return "fake-model";
    }

    @Override
    public String getPromptVersion() {
        return "v1";
    }
}
