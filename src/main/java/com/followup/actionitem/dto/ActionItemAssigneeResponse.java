package com.followup.actionitem.dto;

import com.followup.user.entity.User;

public record ActionItemAssigneeResponse(
        Long userId,
        String name,
        String email
) {

    public static ActionItemAssigneeResponse from(User user) {
        return new ActionItemAssigneeResponse(user.getId(), user.getName(), user.getEmail());
    }
}
