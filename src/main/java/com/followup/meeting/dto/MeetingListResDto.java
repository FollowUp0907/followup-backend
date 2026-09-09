package com.followup.meeting.dto;

import com.followup.meeting.entity.Meeting;
import com.followup.meeting.entity.MeetingStatus;
import java.time.LocalDateTime;

public record MeetingListResDto(
        Long id,
        String title,
        LocalDateTime scheduledAt,
        MeetingStatus status,
        Long createdBy,
        LocalDateTime createdAt
) {

    public static MeetingListResDto from(Meeting meeting) {
        return new MeetingListResDto(
                meeting.getId(),
                meeting.getTitle(),
                meeting.getScheduledAt(),
                meeting.getStatus(),
                meeting.getCreatedBy().getId(),
                meeting.getCreatedAt()
        );
    }
}
