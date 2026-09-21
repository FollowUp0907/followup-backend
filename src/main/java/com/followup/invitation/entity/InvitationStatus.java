package com.followup.invitation.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프로젝트 초대 상태")
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    CANCELLED
}
