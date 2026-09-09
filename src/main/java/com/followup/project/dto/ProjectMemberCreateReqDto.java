package com.followup.project.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ProjectMemberCreateReqDto(

        @NotBlank
        @Email
        String email
) {
}
