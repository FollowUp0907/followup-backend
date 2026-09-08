package com.followup.meeting.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public record MeetingUpdateRequest(

        @Size(max = 200)
        String title,

        LocalDateTime scheduledAt,

        String content,

        List<Long> participantIds
) {
}
