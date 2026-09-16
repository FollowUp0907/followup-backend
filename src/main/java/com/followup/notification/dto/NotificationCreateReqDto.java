package com.followup.notification.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record NotificationCreateReqDto(
        @NotNull
        LocalDateTime remindAt
) {
}
