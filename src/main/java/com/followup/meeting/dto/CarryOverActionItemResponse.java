package com.followup.meeting.dto;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import java.time.LocalDate;

public record CarryOverActionItemResponse(
        Long actionItemId,
        String title,
        ActionItemStatus status,
        Long assigneeUserId,
        LocalDate dueDate,
        Priority priority
) {

    public static CarryOverActionItemResponse from(ActionItem actionItem) {
        return new CarryOverActionItemResponse(
                actionItem.getId(),
                actionItem.getTitle(),
                actionItem.getStatus(),
                actionItem.getAssignee() != null ? actionItem.getAssignee().getId() : null,
                actionItem.getDueDate(),
                actionItem.getPriority()
        );
    }
}
