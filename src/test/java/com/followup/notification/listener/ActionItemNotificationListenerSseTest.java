package com.followup.notification.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import com.followup.actionitem.event.TaskAssignedEvent;
import com.followup.meeting.repository.MeetingParticipantRepository;
import com.followup.notification.entity.Notification;
import com.followup.notification.entity.NotificationType;
import com.followup.notification.repository.NotificationRepository;
import com.followup.notification.sse.SseEmitterRegistry;
import com.followup.project.entity.Project;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * DB/스프링 컨텍스트 없이 순수 Mockito로, TaskAssignedEvent가 대상 사용자에게 등록된 SseEmitter로
 * 실제로 전송을 시도하는지만 검증한다(SseEmitterRegistry 자체는 실제 인스턴스를 쓰고, SseEmitter만 mock).
 */
class ActionItemNotificationListenerSseTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final MeetingParticipantRepository meetingParticipantRepository = mock(MeetingParticipantRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SseEmitterRegistry sseEmitterRegistry = new SseEmitterRegistry();

    private final ActionItemNotificationListener listener = new ActionItemNotificationListener(
            notificationRepository, meetingParticipantRepository, userRepository, sseEmitterRegistry);

    @Test
    void onTaskAssigned_sendsToRegisteredEmitterForTargetUser() throws Exception {
        Long targetUserId = 42L;
        User assignee = User.builder().email("a@test.com").password("pw").name("A").build();
        Project project = Project.builder().name("P").createdBy(assignee).build();
        ActionItem item = ActionItem.builder()
                .project(project)
                .title("Task")
                .status(ActionItemStatus.TODO)
                .priority(Priority.MEDIUM)
                .build();
        Notification saved = Notification.builder()
                .user(assignee)
                .project(project)
                .actionItem(item)
                .taskTitle("Task")
                .remindAt(LocalDateTime.now())
                .type(NotificationType.TASK_CREATED)
                .build();
        when(userRepository.getReferenceById(targetUserId)).thenReturn(assignee);
        when(notificationRepository.save(any())).thenReturn(saved);

        SseEmitter mockEmitter = mock(SseEmitter.class);
        sseEmitterRegistry.register(targetUserId, mockEmitter);

        listener.onTaskAssigned(new TaskAssignedEvent(item, 1L, targetUserId));

        verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void onTaskAssigned_noRegisteredEmitter_doesNotThrow() {
        Long targetUserId = 99L;
        User assignee = User.builder().email("b@test.com").password("pw").name("B").build();
        Project project = Project.builder().name("P").createdBy(assignee).build();
        ActionItem item = ActionItem.builder()
                .project(project)
                .title("Task")
                .status(ActionItemStatus.TODO)
                .priority(Priority.MEDIUM)
                .build();
        Notification saved = Notification.builder()
                .user(assignee)
                .project(project)
                .actionItem(item)
                .taskTitle("Task")
                .remindAt(LocalDateTime.now())
                .type(NotificationType.TASK_CREATED)
                .build();
        when(userRepository.getReferenceById(targetUserId)).thenReturn(assignee);
        when(notificationRepository.save(any())).thenReturn(saved);

        listener.onTaskAssigned(new TaskAssignedEvent(item, 1L, targetUserId));
    }
}
