package com.followup.actionitem.event;

import com.followup.actionitem.entity.ActionItem;

/** 업무 상태가 DONE이 아니었다가 DONE으로 바뀌었을 때 발행된다(이미 DONE인 상태에서 다시 DONE으로 오는 경우는 제외). */
public record TaskCompletedEvent(ActionItem item, Long actorId) {
}
