package com.followup.project.event;

import com.followup.project.entity.Project;
import com.followup.user.entity.User;

/**
 * 새 사용자가 프로젝트에 합류했을 때(초대 수락 등) 발행된다. joinedUser 본인은 알림 대상에서
 * 제외되고, 그 시점의 나머지 멤버 전원이 대상이다.
 */
public record MemberJoinedEvent(Project project, User joinedUser) {
}
