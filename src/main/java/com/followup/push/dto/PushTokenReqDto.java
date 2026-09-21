package com.followup.push.dto;

import jakarta.validation.constraints.NotBlank;

public record PushTokenReqDto(
        @NotBlank
        String token
) {
}
