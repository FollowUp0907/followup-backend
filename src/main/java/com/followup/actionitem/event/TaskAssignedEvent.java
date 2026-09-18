package com.followup.actionitem.event;

import com.followup.actionitem.entity.ActionItem;

/**
 * 업무의 담당자(주 담당자 또는 보조 담당자)가 actor 본인이 아닌 사람으로 정해졌을 때, 그 대상자 1명당
 * 1건씩 발행된다. targetUserId가 대상자다 — item에서 assignee를 직접 읽지 않는 것은 보조 담당자
 * 배정도 같은 이벤트로 표현하기 위해서다.
 */
public record TaskAssignedEvent(ActionItem item, Long actorId, Long targetUserId) {
}
