package com.followup.actionitem.dto;

import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ActionItemUpdateReqDto(

        @Size(max = 255)
        String title,

        String description,

        Long assigneeUserId,

        LocalDate dueDate,

        ActionItemStatus status,

        Priority priority
) {
}
