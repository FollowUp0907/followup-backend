package com.followup.auth.dto;

public record TokenResDto(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
