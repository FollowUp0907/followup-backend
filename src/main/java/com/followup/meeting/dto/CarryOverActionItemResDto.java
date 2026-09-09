package com.followup.meeting.dto;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import java.time.LocalDate;

public record CarryOverActionItemResDto(
        Long actionItemId,
        String title,
        ActionItemStatus status,
        Long assigneeUserId,
        LocalDate dueDate,
        Priority priority
) {

    public static CarryOverActionItemResDto from(ActionItem actionItem) {
        return new CarryOverActionItemResDto(
                actionItem.getId(),
                actionItem.getTitle(),
                actionItem.getStatus(),
                actionItem.getAssignee() != null ? actionItem.getAssignee().getId() : null,
                actionItem.getDueDate(),
                actionItem.getPriority()
        );
    }
}
