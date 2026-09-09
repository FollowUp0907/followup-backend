package com.followup.auth.dto;

import com.followup.user.entity.User;

public record SignupResDto(
        Long userId,
        String email,
        String name
) {

    public static SignupResDto from(User user) {
        return new SignupResDto(user.getId(), user.getEmail(), user.getName());
    }
}
