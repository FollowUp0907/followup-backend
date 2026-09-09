package com.followup.ai.dto;

import com.followup.actionitem.entity.Priority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record AnalysisConfirmReqDto(
        @Valid List<DecisionConfirmItem> decisions,
        @Valid List<ActionItemConfirmItem> actionItems
) {

    public record DecisionConfirmItem(
            @NotBlank
            String content
    ) {
    }

    public record ActionItemConfirmItem(
            @NotBlank
            @Size(max = 255)
            String title,

            String description,

            Long assigneeUserId,

            LocalDate dueDate,

            Priority priority,

            String priorityReason
    ) {
    }
}
