package com.followup.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public record MeetingCreateRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @NotNull
        LocalDateTime scheduledAt,

        String content,

        List<Long> participantIds,

        List<Long> carryOverActionItemIds
) {
}
