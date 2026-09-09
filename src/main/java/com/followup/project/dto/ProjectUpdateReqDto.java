package com.followup.project.dto;

import jakarta.validation.constraints.Size;

public record ProjectUpdateReqDto(

        @Size(max = 150)
        String name,

        String description
) {
}
