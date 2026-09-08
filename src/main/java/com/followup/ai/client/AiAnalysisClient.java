package com.followup.ai.client;

import com.followup.ai.dto.AiDraftResult;
import java.time.LocalDateTime;

public interface AiAnalysisClient {

    /** @param meetingScheduledAt 연도 없는 날짜 표현의 기준 연도. null이면 해당 날짜는 추정하지 말고 null로 처리한다. */
    AiDraftResult analyze(String meetingContent, LocalDateTime meetingScheduledAt);

    String getModelName();

    String getPromptVersion();
}
