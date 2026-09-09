package com.followup.actionitem.dto;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ActionItemDetailResDto(
        Long id,
        Long projectId,
        String title,
        String description,
        ActionItemAssigneeResDto assignee,
        LocalDate dueDate,
        ActionItemStatus status,
        Priority priority,
        String priorityReason,
        Long originMeetingId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt
) {

    public static ActionItemDetailResDto from(ActionItem actionItem) {
        return new ActionItemDetailResDto(
                actionItem.getId(),
                actionItem.getProject().getId(),
                actionItem.getTitle(),
                actionItem.getDescription(),
                actionItem.getAssignee() != null ? ActionItemAssigneeResDto.from(actionItem.getAssignee()) : null,
                actionItem.getDueDate(),
                actionItem.getStatus(),
                actionItem.getPriority(),
                actionItem.getPriorityReason(),
                actionItem.getOriginMeeting() != null ? actionItem.getOriginMeeting().getId() : null,
                actionItem.getCreatedAt(),
                actionItem.getUpdatedAt(),
                actionItem.getCompletedAt()
        );
    }
}
