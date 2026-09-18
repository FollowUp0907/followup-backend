package com.followup.actionitem.dto;

import com.followup.actionitem.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record ActionItemCreateReqDto(

        @NotBlank
        @Size(max = 255)
        String title,

        String description,

        /** optional — 담당자로 지정할 사용자 id 목록(여러 명 가능). */
        List<Long> assigneeUserIds,

        LocalDate dueDate,

        Priority priority
) {
}
