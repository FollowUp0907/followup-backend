package com.followup.actionitem.dto;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import com.followup.meeting.entity.Meeting;
import com.followup.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record ActionItemDetailResDto(
        Long id,
        Long projectId,
        String title,
        String description,
        List<ActionItemAssigneeResDto> assignees,
        LocalDate dueDate,
        ActionItemStatus status,
        Priority priority,
        String priorityReason,
        Long originMeetingId,
        String originMeetingTitle,
        boolean originMeetingDeleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt
) {

    public static ActionItemDetailResDto from(ActionItem actionItem) {
        Meeting originMeeting = actionItem.getOriginMeeting();
        return new ActionItemDetailResDto(
                actionItem.getId(),
                actionItem.getProject().getId(),
                actionItem.getTitle(),
                actionItem.getDescription(),
                actionItem.getAssignees().stream()
                        .sorted(Comparator.comparing(User::getId))
                        .map(ActionItemAssigneeResDto::from)
                        .toList(),
                actionItem.getDueDate(),
                actionItem.getStatus(),
                actionItem.getPriority(),
                actionItem.getPriorityReason(),
                originMeeting != null ? originMeeting.getId() : null,
                originMeeting != null ? originMeeting.getTitle() : null,
                originMeeting != null && originMeeting.getDeletedAt() != null,
                actionItem.getCreatedAt(),
                actionItem.getUpdatedAt(),
                actionItem.getCompletedAt()
        );
    }
}
