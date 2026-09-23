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

    /** 만든 사람이 탈퇴해 createdBy가 null인 회의도 있을 수 있다(회의 자체는 유지되므로). */
    public static MeetingListResDto from(Meeting meeting) {
        return new MeetingListResDto(
                meeting.getId(),
                meeting.getTitle(),
                meeting.getScheduledAt(),
                meeting.getStatus(),
                meeting.getCreatedBy() != null ? meeting.getCreatedBy().getId() : null,
                meeting.getCreatedAt()
        );
    }
}
