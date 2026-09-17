package com.followup.notification.dto;

import com.followup.notification.entity.Notification;
import com.followup.notification.entity.NotificationType;
import java.time.LocalDateTime;

public record NotificationResDto(
        Long id,
        Long userId,
        Long projectId,
        Long actionItemId,
        String taskTitle,
        NotificationType type,
        LocalDateTime remindAt,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {

    public static NotificationResDto of(Notification notification) {
        return new NotificationResDto(
                notification.getId(),
                notification.getUser().getId(),
                notification.getProject().getId(),
                notification.getActionItem().getId(),
                notification.getTaskTitle(),
                notification.getType(),
                notification.getRemindAt(),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }
}
