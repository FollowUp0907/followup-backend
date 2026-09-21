package com.followup.push.service;

import com.followup.notification.entity.Notification;
import com.followup.notification.entity.NotificationType;
import com.followup.push.client.FcmClient;
import com.followup.push.entity.PushSubscription;
import com.followup.push.repository.PushSubscriptionRepository;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 앱이 완전히 닫혀 있을 때를 위한 추가 채널이다 — SSE/폴링은 그대로 두고 그 위에 얹는다.
 * 발송 실패(전체/일부 토큰)는 알림 저장 자체에 영향을 주면 안 되므로 예외를 전부 삼키고 로그만 남긴다
 * (InvitationEmailService의 메일 발송 실패 처리와 동일한 원칙).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationSender {

    private static final Map<NotificationType, String> TITLES = Map.of(
            NotificationType.TASK_CREATED, "새 업무가 배정되었습니다",
            NotificationType.TASK_UPDATED, "담당 업무가 수정되었습니다",
            NotificationType.TASK_COMPLETED, "업무가 완료되었습니다",
            NotificationType.MEMBER_JOINED, "새 구성원이 합류했습니다"
    );
    private static final String DEFAULT_TITLE = "새 알림이 있습니다";

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final Optional<FcmClient> fcmClient;

    public void send(Notification notification) {
        if (fcmClient.isEmpty()) {
            log.debug("FCM이 초기화되지 않아 푸시 발송을 건너뜁니다. notificationId={}", notification.getId());
            return;
        }

        List<String> tokens = pushSubscriptionRepository.findAllByUserId(notification.getUser().getId()).stream()
                .map(PushSubscription::getToken)
                .toList();
        if (tokens.isEmpty()) {
            return;
        }

        try {
            MulticastMessage message = buildMessage(tokens, notification);
            BatchResponse response = fcmClient.get().sendEachForMulticast(message);
            cleanUpDeadTokens(tokens, response);
        } catch (Exception e) {
            log.warn("푸시 발송 실패. notificationId={}", notification.getId(), e);
        }
    }

    /**
     * addAllTokens는 SDK상 deprecated 표시가 있지만, 대체로 제시되는 addAllFids는 FCM 등록 토큰과는
     * 다른 개념(Firebase Installation ID)이라 우리 클라이언트가 수집하는 등록 토큰에는 맞지 않는다 —
     * 의도적으로 그대로 쓴다.
     */
    @SuppressWarnings("deprecation")
    private MulticastMessage buildMessage(List<String> tokens, Notification notification) {
        String title = TITLES.getOrDefault(notification.getType(), DEFAULT_TITLE);
        String actionItemId = notification.getActionItem() != null
                ? String.valueOf(notification.getActionItem().getId())
                : "";

        return MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(title)
                        .setBody(notification.getTaskTitle())
                        .build())
                .putData("type", notification.getType().name())
                .putData("projectId", String.valueOf(notification.getProject().getId()))
                .putData("actionItemId", actionItemId)
                .putData("notificationId", String.valueOf(notification.getId()))
                .putData("taskTitle", notification.getTaskTitle())
                .build();
    }

    /** 등록 해제된 토큰(UNREGISTERED/INVALID_ARGUMENT)은 다음 발송에서 또 실패하지 않도록 정리한다. */
    private void cleanUpDeadTokens(List<String> tokens, BatchResponse response) {
        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (sendResponse.isSuccessful()) {
                continue;
            }
            MessagingErrorCode errorCode = sendResponse.getException() != null
                    ? sendResponse.getException().getMessagingErrorCode()
                    : null;
            if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                pushSubscriptionRepository.deleteByToken(tokens.get(i));
            }
        }
    }
}
