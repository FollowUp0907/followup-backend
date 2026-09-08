package com.followup.project.dto;

import com.followup.project.entity.ProjectMember;
import com.followup.project.entity.ProjectRole;
import java.time.LocalDateTime;

public record ProjectMemberResponse(
        Long userId,
        String name,
        String email,
        ProjectRole role,
        LocalDateTime joinedAt
) {

    public static ProjectMemberResponse from(ProjectMember member) {
        return new ProjectMemberResponse(
                member.getUser().getId(),
                member.getUser().getName(),
                member.getUser().getEmail(),
                member.getRole(),
                member.getJoinedAt()
        );
    }
}
