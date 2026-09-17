package com.followup.actionitem.event;

import com.followup.actionitem.entity.ActionItem;

/** 업무의 담당자가 (신규 지정이든 변경이든) actor 본인이 아닌 사람으로 정해졌을 때 발행된다. */
public record TaskAssignedEvent(ActionItem item, Long actorId) {
}
