package com.followup.user.dto;

import com.followup.user.entity.User;

public record MeResDto(
        Long userId,
        String name,
        String email
) {

    public static MeResDto from(User user) {
        return new MeResDto(user.getId(), user.getName(), user.getEmail());
    }
}
