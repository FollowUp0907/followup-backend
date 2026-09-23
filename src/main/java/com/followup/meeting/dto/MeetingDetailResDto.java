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

    /**
     * AI 분석 확정으로 생성된 Decision을 회의 상세 응답에 함께 포함한다.
     * 만든 사람이 탈퇴해 createdBy가 null인 회의도 있을 수 있다(회의 자체는 유지되므로).
     */
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
                meeting.getCreatedBy() != null ? meeting.getCreatedBy().getId() : null,
                meeting.getCreatedAt(),
                meeting.getUpdatedAt(),
                participants.stream().map(MeetingParticipantResDto::from).toList(),
                carryOverActionItems.stream().map(CarryOverActionItemResDto::from).toList(),
                decisions.stream().map(DecisionResDto::from).toList()
        );
    }
}
