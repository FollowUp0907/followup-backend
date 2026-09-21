package com.followup.invitation.dto;

import com.followup.invitation.entity.InvitationStatus;
import com.followup.invitation.entity.ProjectInvitation;
import java.time.LocalDateTime;

public record InvitationResDto(
        Long id,
        String email,
        InvitationStatus status,
        LocalDateTime invitedAt,
        LocalDateTime expiresAt,
        LocalDateTime acceptedAt,
        String invitedByName
) {

    public static InvitationResDto from(ProjectInvitation invitation) {
        return new InvitationResDto(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getStatus(),
                invitation.getInvitedAt(),
                invitation.getExpiresAt(),
                invitation.getAcceptedAt(),
                invitation.getInvitedBy().getName()
        );
    }
}
