package com.followup.actionitem.dto;

import com.followup.user.entity.User;

public record ActionItemAssigneeResDto(
        Long userId,
        String name,
        String email
) {

    public static ActionItemAssigneeResDto from(User user) {
        return new ActionItemAssigneeResDto(user.getId(), user.getName(), user.getEmail());
    }
}
