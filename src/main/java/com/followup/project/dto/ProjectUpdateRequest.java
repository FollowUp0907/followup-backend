package com.followup.project.dto;

import jakarta.validation.constraints.Size;

public record ProjectUpdateRequest(

        @Size(max = 150)
        String name,

        String description
) {
}
