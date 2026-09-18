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

            /** AI는 최대 한 명만 추천하지만, 확정 시점에 사용자가 여러 명을 추가로 선택할 수 있다. */
            List<Long> assigneeUserIds,

            LocalDate dueDate,

            Priority priority,

            String priorityReason
    ) {
    }
}
