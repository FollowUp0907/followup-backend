package com.followup.actionitem.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "후속 업무 상태")
public enum ActionItemStatus {
    TODO,
    IN_PROGRESS,
    DONE
}
