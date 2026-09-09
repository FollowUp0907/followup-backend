package com.followup.actionitem.dto;

import com.followup.actionitem.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ActionItemCreateReqDto(

        @NotBlank
        @Size(max = 255)
        String title,

        String description,

        Long assigneeUserId,

        LocalDate dueDate,

        Priority priority
) {
}
