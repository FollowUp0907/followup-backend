package com.followup.actionitem.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "후속 업무 우선순위")
public enum Priority {
    HIGH,
    MEDIUM,
    LOW
}
