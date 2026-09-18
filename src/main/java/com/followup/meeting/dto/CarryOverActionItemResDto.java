package com.followup.meeting.dto;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import com.followup.user.entity.User;
import java.time.LocalDate;
import java.util.List;

public record CarryOverActionItemResDto(
        Long actionItemId,
        String title,
        ActionItemStatus status,
        List<Long> assigneeUserIds,
        LocalDate dueDate,
        Priority priority
) {

    public static CarryOverActionItemResDto from(ActionItem actionItem) {
        return new CarryOverActionItemResDto(
                actionItem.getId(),
                actionItem.getTitle(),
                actionItem.getStatus(),
                actionItem.getAssignees().stream()
                        .map(User::getId)
                        .sorted()
                        .toList(),
                actionItem.getDueDate(),
                actionItem.getPriority()
        );
    }
}
