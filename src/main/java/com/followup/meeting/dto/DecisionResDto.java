package com.followup.meeting.dto;

import com.followup.meeting.entity.Decision;
import java.time.LocalDateTime;

public record DecisionResDto(
        Long id,
        String content,
        LocalDateTime createdAt
) {

    public static DecisionResDto from(Decision decision) {
        return new DecisionResDto(
                decision.getId(),
                decision.getContent(),
                decision.getCreatedAt()
        );
    }
}
