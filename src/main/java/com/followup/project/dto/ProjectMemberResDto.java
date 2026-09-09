package com.followup.project.dto;

import com.followup.project.entity.ProjectMember;
import com.followup.project.entity.ProjectRole;
import java.time.LocalDateTime;

public record ProjectMemberResDto(
        Long userId,
        String name,
        String email,
        ProjectRole role,
        LocalDateTime joinedAt
) {

    public static ProjectMemberResDto from(ProjectMember member) {
        return new ProjectMemberResDto(
                member.getUser().getId(),
                member.getUser().getName(),
                member.getUser().getEmail(),
                member.getRole(),
                member.getJoinedAt()
        );
    }
}
