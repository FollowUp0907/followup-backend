package com.followup.project.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프로젝트 내 역할")
public enum ProjectRole {
    OWNER,
    MEMBER
}
