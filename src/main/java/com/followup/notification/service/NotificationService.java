package com.followup.notification.service;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.notification.dto.NotificationCreateReqDto;
import com.followup.notification.dto.NotificationResDto;
import com.followup.notification.entity.Notification;
import com.followup.notification.repository.NotificationRepository;
import com.followup.project.repository.ProjectMemberRepository;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ActionItemRepository actionItemRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public List<NotificationResDto> getNotifications(boolean dueOnly) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        List<Notification> notifications = dueOnly
                ? notificationRepository.findAllByUserIdAndRemindAtLessThanEqualOrderByRemindAtDesc(
                        currentUserId, LocalDateTime.now())
                : notificationRepository.findAllByUserIdOrderByRemindAtDesc(currentUserId);
        return notifications.stream().map(NotificationResDto::of).toList();
    }

    /** 업무 하나당 알림은 1건만 유지한다. 이미 있으면 설정자/제목/시각을 덮어쓰고, 없으면 새로 만든다. */
    @Transactional
    public NotificationResDto createOrUpdateNotification(Long actionItemId, NotificationCreateReqDto request) {
        ActionItem actionItem = getActionItemOrThrow(actionItemId);
        Long currentUserId = currentUserProvider.getCurrentUserId();
        requireMember(actionItem.getProject().getId(), currentUserId);

        User user = userRepository.getReferenceById(currentUserId);
        Notification notification = notificationRepository.findByActionItemId(actionItemId).orElse(null);

        if (notification == null) {
            notification = Notification.builder()
                    .user(user)
                    .project(actionItem.getProject())
                    .actionItem(actionItem)
                    .taskTitle(actionItem.getTitle())
                    .remindAt(request.remindAt())
                    .build();
            notificationRepository.save(notification);
        } else {
            notification.reassignTo(user);
            notification.update(actionItem.getTitle(), request.remindAt());
        }

        return NotificationResDto.of(notification);
    }

    @Transactional
    public void deleteNotification(Long notificationId) {
        Notification notification = getNotificationOrThrow(notificationId);
        requireOwner(notification, currentUserProvider.getCurrentUserId());
        notificationRepository.delete(notification);
    }

    @Transactional
    public NotificationResDto markAsRead(Long notificationId) {
        Notification notification = getNotificationOrThrow(notificationId);
        requireOwner(notification, currentUserProvider.getCurrentUserId());
        notification.markAsRead();
        return NotificationResDto.of(notification);
    }

    @Transactional
    public void markAllAsRead() {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        notificationRepository.findAllByUserIdAndReadAtIsNullAndRemindAtLessThanEqual(
                        currentUserId, LocalDateTime.now())
                .forEach(Notification::markAsRead);
    }

    private ActionItem getActionItemOrThrow(Long actionItemId) {
        return actionItemRepository.findById(actionItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTION_ITEM_NOT_FOUND));
    }

    private Notification getNotificationOrThrow(Long notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
    }

    private void requireMember(Long projectId, Long userId) {
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
    }

    private void requireOwner(Notification notification, Long userId) {
        if (!notification.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }
    }
}
