package com.followup.meeting.dto;

import com.followup.actionitem.entity.ActionItem;
import com.followup.meeting.entity.Decision;
import com.followup.meeting.entity.Meeting;
import com.followup.meeting.entity.MeetingParticipant;
import com.followup.meeting.entity.MeetingStatus;
import java.time.LocalDateTime;
import java.util.List;

public record MeetingDetailResDto(
        Long id,
        Long projectId,
        String title,
        LocalDateTime scheduledAt,
        String content,
        MeetingStatus status,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<MeetingParticipantResDto> participants,
        List<CarryOverActionItemResDto> carryOverActionItems,
        List<DecisionResDto> decisions
) {

    /** AI 분석 확정으로 생성된 Decision을 회의 상세 응답에 함께 포함한다. */
    public static MeetingDetailResDto from(Meeting meeting,
                                              List<MeetingParticipant> participants,
                                              List<ActionItem> carryOverActionItems,
                                              List<Decision> decisions) {
        return new MeetingDetailResDto(
                meeting.getId(),
                meeting.getProject().getId(),
                meeting.getTitle(),
                meeting.getScheduledAt(),
                meeting.getContent(),
                meeting.getStatus(),
                meeting.getCreatedBy().getId(),
                meeting.getCreatedAt(),
                meeting.getUpdatedAt(),
                participants.stream().map(MeetingParticipantResDto::from).toList(),
                carryOverActionItems.stream().map(CarryOverActionItemResDto::from).toList(),
                decisions.stream().map(DecisionResDto::from).toList()
        );
    }
}
