package com.followup.notification.listener;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.event.TaskAssignedEvent;
import com.followup.actionitem.event.TaskCompletedEvent;
import com.followup.actionitem.event.TaskUpdatedEvent;
import com.followup.meeting.repository.MeetingParticipantRepository;
import com.followup.notification.dto.NotificationResDto;
import com.followup.notification.entity.Notification;
import com.followup.notification.entity.NotificationType;
import com.followup.notification.repository.NotificationRepository;
import com.followup.notification.sse.SseEmitterRegistry;
import com.followup.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 업무 이벤트를 알림으로 변환한다. 각 핸들러는 원래 트랜잭션이 커밋된 뒤(AFTER_COMMIT)에만 실행되므로,
 * 여기서 만드는 알림은 실제로 반영된 변경에 대해서만 생성된다. AFTER_COMMIT 시점엔 원래 트랜잭션이
 * 이미 끝나 있어 별도 트랜잭션이 필요하므로 각 메서드에 @Transactional을 직접 붙인다.
 */
@Component
@RequiredArgsConstructor
public class ActionItemNotificationListener {

    private final NotificationRepository notificationRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final UserRepository userRepository;
    private final SseEmitterRegistry sseEmitterRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTaskAssigned(TaskAssignedEvent event) {
        notify(event.item(), event.targetUserId(), NotificationType.TASK_CREATED);
    }

    /** 담당자 전원(actor 제외, 중복 인원은 1건으로)에게 알린다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTaskUpdated(TaskUpdatedEvent event) {
        ActionItem item = event.item();
        Set<Long> recipientIds = new LinkedHashSet<>();
        item.getAssignees().forEach(user -> recipientIds.add(user.getId()));
        recipientIds.remove(event.actorId());

        recipientIds.forEach(userId -> notify(item, userId, NotificationType.TASK_UPDATED));
    }

    /**
     * 담당자 전원/생성자/origin 회의 참여자에게 알린다(actor 포함, 중복 인원은 1건으로 합쳐진다).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTaskCompleted(TaskCompletedEvent event) {
        ActionItem item = event.item();
        Set<Long> recipientIds = new LinkedHashSet<>();
        item.getAssignees().forEach(user -> recipientIds.add(user.getId()));
        if (item.getCreatedBy() != null) {
            recipientIds.add(item.getCreatedBy().getId());
        }
        if (item.getOriginMeeting() != null) {
            meetingParticipantRepository.findAllByMeetingId(item.getOriginMeeting().getId())
                    .forEach(participant -> recipientIds.add(participant.getUser().getId()));
        }

        recipientIds.forEach(userId -> notify(item, userId, NotificationType.TASK_COMPLETED));
    }

    /** 저장 직후 SSE로도 밀어 보낸다 — 폴링은 그대로 유지되고 SSE는 추가되는 실시간 채널이다. */
    private void notify(ActionItem item, Long userId, NotificationType type) {
        Notification notification = notificationRepository.save(Notification.builder()
                .user(userRepository.getReferenceById(userId))
                .project(item.getProject())
                .actionItem(item)
                .taskTitle(item.getTitle())
                .remindAt(LocalDateTime.now())
                .type(type)
                .build());
        sseEmitterRegistry.sendToUser(userId, "notification", NotificationResDto.of(notification));
    }
}
