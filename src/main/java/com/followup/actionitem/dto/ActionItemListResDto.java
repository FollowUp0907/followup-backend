package com.followup.actionitem.dto;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import com.followup.user.entity.User;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record ActionItemListResDto(
        Long id,
        String title,
        ActionItemStatus status,
        Priority priority,
        List<ActionItemAssigneeResDto> assignees,
        LocalDate dueDate,
        Long projectId
) {

    public static ActionItemListResDto from(ActionItem actionItem) {
        return new ActionItemListResDto(
                actionItem.getId(),
                actionItem.getTitle(),
                actionItem.getStatus(),
                actionItem.getPriority(),
                actionItem.getAssignees().stream()
                        .sorted(Comparator.comparing(User::getId))
                        .map(ActionItemAssigneeResDto::from)
                        .toList(),
                actionItem.getDueDate(),
                actionItem.getProject().getId()
        );
    }
}
