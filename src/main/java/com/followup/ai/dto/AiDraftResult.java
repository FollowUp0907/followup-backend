package com.followup.ai.dto;

import com.followup.actionitem.entity.Priority;
import java.time.LocalDate;
import java.util.List;

public record AiDraftResult(
        List<DraftDecision> decisions,
        List<DraftActionItem> actionItems
) {

    public record DraftDecision(
            String content
    ) {
    }

    public record DraftActionItem(
            String title,
            String description,
            String assigneeName,
            LocalDate dueDate,
            Priority priority,
            String priorityReason
    ) {
    }
}
