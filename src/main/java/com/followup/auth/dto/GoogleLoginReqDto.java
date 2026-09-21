package com.followup.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginReqDto(
        @NotBlank
        String idToken
) {
}
