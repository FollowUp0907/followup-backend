package com.followup.invitation.dto;

import com.followup.invitation.entity.InvitationStatus;
import com.followup.invitation.entity.ProjectInvitation;
import java.time.LocalDateTime;

/** 토큰 소유자라면 로그인 없이도 볼 수 있는 응답이라, 민감 정보(토큰 자체 등)는 담지 않는다. */
public record InvitationViewResDto(
        Long projectId,
        String projectName,
        String email,
        String status,
        String invitedByName,
        LocalDateTime expiresAt
) {

    public static InvitationViewResDto from(ProjectInvitation invitation) {
        String status = invitation.getStatus() == InvitationStatus.PENDING && invitation.isExpired()
                ? "EXPIRED"
                : invitation.getStatus().name();
        return new InvitationViewResDto(
                invitation.getProject().getId(),
                invitation.getProject().getName(),
                invitation.getEmail(),
                status,
                invitation.getInvitedBy().getName(),
                invitation.getExpiresAt()
        );
    }
}
