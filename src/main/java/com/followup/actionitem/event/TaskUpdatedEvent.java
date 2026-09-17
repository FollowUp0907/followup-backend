package com.followup.actionitem.event;

import com.followup.actionitem.entity.ActionItem;

/** 담당자 변경이 아닌 내용(title/description/dueDate/priority) 수정이, 담당자가 아닌 사람에 의해 일어났을 때 발행된다. */
public record TaskUpdatedEvent(ActionItem item, Long actorId) {
}
