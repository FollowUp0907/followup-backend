package com.followup.actionitem.dto;

import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record ActionItemUpdateReqDto(

        @Size(max = 255)
        String title,

        String description,

        /** null이면 그대로 두고, 값이 있으면(빈 리스트 포함) 담당자 전체를 이 목록으로 교체한다. */
        List<Long> assigneeUserIds,

        LocalDate dueDate,

        ActionItemStatus status,

        Priority priority
) {
}
