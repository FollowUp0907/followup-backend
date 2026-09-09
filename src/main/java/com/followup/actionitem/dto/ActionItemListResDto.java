package com.followup.actionitem.dto;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import java.time.LocalDate;

public record ActionItemListResDto(
        Long id,
        String title,
        ActionItemStatus status,
        Priority priority,
        Long assigneeUserId,
        LocalDate dueDate,
        Long projectId
) {

    public static ActionItemListResDto from(ActionItem actionItem) {
        return new ActionItemListResDto(
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
