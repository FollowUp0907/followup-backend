package com.followup.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMeReqDto(
        @NotBlank
        @Size(min = 1, max = 50)
        String name
) {
}
