package com.followup.project.dto;

import com.followup.project.entity.Project;
import java.time.LocalDateTime;

public record ProjectResDto(
        Long id,
        String name,
        String description,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ProjectResDto from(Project project) {
        return new ProjectResDto(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getCreatedBy().getId(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
