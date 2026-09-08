package com.followup.meeting.dto;

import com.followup.actionitem.entity.ActionItem;
import com.followup.meeting.entity.Meeting;
import com.followup.meeting.entity.MeetingParticipant;
import com.followup.meeting.entity.MeetingStatus;
import java.time.LocalDateTime;
import java.util.List;

public record MeetingDetailResponse(
        Long id,
        Long projectId,
        String title,
        LocalDateTime scheduledAt,
        String content,
        MeetingStatus status,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<MeetingParticipantResponse> participants,
        List<CarryOverActionItemResponse> carryOverActionItems
) {

    public static MeetingDetailResponse from(Meeting meeting,
                                              List<MeetingParticipant> participants,
                                              List<ActionItem> carryOverActionItems) {
        return new MeetingDetailResponse(
                meeting.getId(),
                meeting.getProject().getId(),
                meeting.getTitle(),
                meeting.getScheduledAt(),
                meeting.getContent(),
                meeting.getStatus(),
                meeting.getCreatedBy().getId(),
                meeting.getCreatedAt(),
                meeting.getUpdatedAt(),
                participants.stream().map(MeetingParticipantResponse::from).toList(),
                carryOverActionItems.stream().map(CarryOverActionItemResponse::from).toList()
        );
    }
}
