package com.followup.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import com.followup.notification.entity.Notification;
import com.followup.notification.entity.NotificationType;
import com.followup.project.entity.Project;
import com.followup.push.client.FcmClient;
import com.followup.push.entity.PushSubscription;
import com.followup.push.repository.PushSubscriptionRepository;
import com.followup.user.entity.User;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * FirebaseMessaging을 실제로 호출하지 않도록 FcmClient를 mock으로 대체한 순수 Mockito 테스트다
 * (ActionItemNotificationListenerSseTest와 같은 스타일 — DB/스프링 컨텍스트 없이 빠르게 검증한다).
 */
class PushNotificationSenderTest {

    private final PushSubscriptionRepository pushSubscriptionRepository = mock(PushSubscriptionRepository.class);
    private final FcmClient fcmClient = mock(FcmClient.class);

    private final PushNotificationSender senderWithClient =
            new PushNotificationSender(pushSubscriptionRepository, Optional.of(fcmClient));
    private final PushNotificationSender senderWithoutClient =
            new PushNotificationSender(pushSubscriptionRepository, Optional.empty());

    private User user(Long id) {
        User u = User.builder().email("u" + id + "@test.com").password("pw").name("U" + id).build();
        setId(u, id);
        return u;
    }

    /** id는 @GeneratedValue라 빌더로 못 채우므로, 리플렉션으로 직접 심어 테스트용 픽스처를 만든다. */
    private void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Notification notification(Long notificationId, User recipient, Long actionItemId) {
        Project project = Project.builder().name("P").createdBy(recipient).build();
        setId(project, 10L);
        ActionItem item = null;
        if (actionItemId != null) {
            item = ActionItem.builder()
                    .project(project)
                    .title("Task")
                    .status(ActionItemStatus.TODO)
                    .priority(Priority.MEDIUM)
                    .build();
            setId(item, actionItemId);
        }
        Notification notification = Notification.builder()
                .user(recipient)
                .project(project)
                .actionItem(item)
                .taskTitle("My Task")
                .remindAt(LocalDateTime.now())
                .type(NotificationType.TASK_CREATED)
                .build();
        setId(notification, notificationId);
        return notification;
    }

    private PushSubscription subscription(String token) {
        return PushSubscription.builder().user(user(1L)).token(token).build();
    }

    @Test
    void send_noTokensRegistered_doesNotCallFcm() throws Exception {
        User recipient = user(1L);
        when(pushSubscriptionRepository.findAllByUserId(1L)).thenReturn(List.of());

        senderWithClient.send(notification(100L, recipient, null));

        verify(fcmClient, never()).sendEachForMulticast(any());
    }

    @Test
    void send_firebaseNotInitialized_doesNotCallFcm() {
        User recipient = user(1L);

        assertThatCode(() -> senderWithoutClient.send(notification(100L, recipient, null)))
                .doesNotThrowAnyException();

        verify(pushSubscriptionRepository, never()).findAllByUserId(any());
    }

    /** MulticastMessage는 조회용 getter가 없는 write-only 빌더 객체라, 실제로 1번 호출됐는지만 검증한다. */
    @Test
    void send_withRegisteredTokens_callsFcmOnce() throws Exception {
        User recipient = user(1L);
        when(pushSubscriptionRepository.findAllByUserId(1L))
                .thenReturn(List.of(subscription("token-a"), subscription("token-b")));
        BatchResponse allSuccess = mock(BatchResponse.class);
        SendResponse ok = mock(SendResponse.class);
        when(ok.isSuccessful()).thenReturn(true);
        when(allSuccess.getResponses()).thenReturn(List.of(ok, ok));
        when(fcmClient.sendEachForMulticast(any())).thenReturn(allSuccess);

        senderWithClient.send(notification(100L, recipient, 55L));

        verify(fcmClient).sendEachForMulticast(any(MulticastMessage.class));
    }

    @Test
    void send_partialFailureWithUnregisteredToken_deletesDeadTokenOnly() throws Exception {
        User recipient = user(1L);
        when(pushSubscriptionRepository.findAllByUserId(1L))
                .thenReturn(List.of(subscription("alive-token"), subscription("dead-token")));

        SendResponse successResponse = mock(SendResponse.class);
        when(successResponse.isSuccessful()).thenReturn(true);

        FirebaseMessagingException unregisteredException = mock(FirebaseMessagingException.class);
        when(unregisteredException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        SendResponse failedResponse = mock(SendResponse.class);
        when(failedResponse.isSuccessful()).thenReturn(false);
        when(failedResponse.getException()).thenReturn(unregisteredException);

        BatchResponse batchResponse = mock(BatchResponse.class);
        when(batchResponse.getResponses()).thenReturn(List.of(successResponse, failedResponse));
        when(fcmClient.sendEachForMulticast(any())).thenReturn(batchResponse);

        senderWithClient.send(notification(100L, recipient, null));

        verify(pushSubscriptionRepository).deleteByToken(eq("dead-token"));
        verify(pushSubscriptionRepository, never()).deleteByToken(eq("alive-token"));
    }

    @Test
    void send_otherFailureReason_doesNotDeleteToken() throws Exception {
        User recipient = user(1L);
        when(pushSubscriptionRepository.findAllByUserId(1L)).thenReturn(List.of(subscription("token-a")));

        FirebaseMessagingException internalException = mock(FirebaseMessagingException.class);
        when(internalException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INTERNAL);
        SendResponse failedResponse = mock(SendResponse.class);
        when(failedResponse.isSuccessful()).thenReturn(false);
        when(failedResponse.getException()).thenReturn(internalException);

        BatchResponse batchResponse = mock(BatchResponse.class);
        when(batchResponse.getResponses()).thenReturn(List.of(failedResponse));
        when(fcmClient.sendEachForMulticast(any())).thenReturn(batchResponse);

        senderWithClient.send(notification(100L, recipient, null));

        verify(pushSubscriptionRepository, never()).deleteByToken(any());
    }

    @Test
    void send_fcmThrows_doesNotPropagate() throws Exception {
        User recipient = user(1L);
        when(pushSubscriptionRepository.findAllByUserId(1L)).thenReturn(List.of(subscription("token-a")));
        when(fcmClient.sendEachForMulticast(any())).thenThrow(mock(FirebaseMessagingException.class));

        assertThatCode(() -> senderWithClient.send(notification(100L, recipient, null)))
                .doesNotThrowAnyException();
    }

    // ---- buildLink ----

    @Test
    void buildLink_withActionItemId_pointsToTaskDetail() {
        String link = PushNotificationSender.buildLink("https://app.example.com", "12", "34");

        assertThat(link).isEqualTo("https://app.example.com/projects/12/tasks/34");
    }

    @Test
    void buildLink_withoutActionItemId_pointsToProject() {
        String link = PushNotificationSender.buildLink("https://app.example.com", "12", "");

        assertThat(link).isEqualTo("https://app.example.com/projects/12");
    }
}
