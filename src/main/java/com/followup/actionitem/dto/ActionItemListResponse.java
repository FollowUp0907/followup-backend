package com.followup.actionitem.dto;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import java.time.LocalDate;

public record ActionItemListResponse(
        Long id,
        String title,
        ActionItemStatus status,
        Priority priority,
        Long assigneeUserId,
        LocalDate dueDate,
        Long projectId
) {

    public static ActionItemListResponse from(ActionItem actionItem) {
        return new ActionItemListResponse(
                actionItem.getId(),
                actionItem.getTitle(),
                actionItem.getStatus(),
                actionItem.getPriority(),
                actionItem.getAssignee() != null ? actionItem.getAssignee().getId() : null,
                actionItem.getDueDate(),
                actionItem.getProject().getId()
        );
    }
}
