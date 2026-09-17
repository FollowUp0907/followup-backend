package com.followup.notification.service;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.notification.dto.NotificationResDto;
import com.followup.notification.entity.Notification;
import com.followup.notification.entity.NotificationRead;
import com.followup.notification.entity.NotificationType;
import com.followup.notification.repository.NotificationReadRepository;
import com.followup.notification.repository.NotificationRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int DUE_SOON_WINDOW_DAYS = 7;
    private static final String DUE_SOON_DEDUP_PREFIX = "duesoon-";

    private final NotificationRepository notificationRepository;
    private final NotificationReadRepository notificationReadRepository;
    private final ActionItemRepository actionItemRepository;
    private final CurrentUserProvider currentUserProvider;

    /**
     * OVERDUE/DUE_SOON은 저장하지 않고 조회 시점마다 담당 업무에서 계산해 실제 저장된 알림과 합친다
     * (저장하면 "업무 완료 시 지운다/안 지운다" 같은 별개 문제가 생기기 때문). 응답 순서는 계산된
     * 항목들(마감일 임박순) 먼저, 그다음 실제 저장된 알림(최신순)이다.
     */
    @Transactional(readOnly = true)
    public List<NotificationResDto> getNotifications(boolean dueOnly) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        LocalDate today = LocalDate.now();

        List<NotificationResDto> result = new ArrayList<>(buildVirtualNotifications(currentUserId, today));

        List<Notification> stored = dueOnly
                ? notificationRepository.findAllByUserIdAndRemindAtLessThanEqualOrderByRemindAtDescIdDesc(
                        currentUserId, LocalDateTime.now())
                : notificationRepository.findAllByUserIdOrderByRemindAtDescIdDesc(currentUserId);
        stored.stream().map(NotificationResDto::of).forEach(result::add);

        return result;
    }

    /** 작업 1의 병합 목록을 그대로 재사용해 안 읽은 것만 센다 — 계산 로직을 중복 작성하지 않기 위함이다. */
    @Transactional(readOnly = true)
    public long getUnreadCount() {
        return getNotifications(false).stream().filter(n -> n.readAt() == null).count();
    }

    @Transactional
    public void deleteNotification(Long notificationId) {
        Notification notification = getNotificationOrThrow(notificationId);
        requireOwner(notification, currentUserProvider.getCurrentUserId());
        notificationRepository.delete(notification);
    }

    /**
     * 음수 id는 계산된 OVERDUE/DUE_SOON 가상 항목이다. OVERDUE는 읽음 개념이 없어 아무 것도 기록하지
     * 않고 현재 상태만 돌려준다. DUE_SOON은 오늘 날짜로 notification_reads에 표시한다.
     */
    @Transactional
    public NotificationResDto markAsRead(Long notificationId) {
        if (notificationId < 0) {
            return markVirtualAsRead(-notificationId);
        }
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

    private List<NotificationResDto> buildVirtualNotifications(Long userId, LocalDate today) {
        return actionItemRepository.findAllByAssigneeIdAndStatusNotAndDueDateIsNotNull(userId, ActionItemStatus.DONE)
                .stream()
                .sorted(Comparator.comparing(ActionItem::getDueDate))
                .flatMap(item -> classify(item.getDueDate(), today)
                        .map(type -> toVirtualDto(item, type, userId, today))
                        .stream())
                .toList();
    }

    private NotificationResDto markVirtualAsRead(Long actionItemId) {
        Long userId = currentUserProvider.getCurrentUserId();
        ActionItem item = actionItemRepository.findById(actionItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        requireAssignee(item, userId);

        LocalDate today = LocalDate.now();
        NotificationType type = classify(item.getDueDate(), today)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (type == NotificationType.DUE_SOON) {
            markDueSoonRead(userId, actionItemId, today);
        }
        return toVirtualDto(item, type, userId, today);
    }

    private void markDueSoonRead(Long userId, Long actionItemId, LocalDate today) {
        String dedupKey = dueSoonDedupKey(actionItemId);
        if (notificationReadRepository.existsByIdUserIdAndIdDedupKeyAndIdReadDate(userId, dedupKey, today)) {
            return;
        }
        try {
            notificationReadRepository.save(new NotificationRead(userId, dedupKey, today));
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 이미 기록됨 — 결과적으로 원하는 상태(오늘 읽음)와 같으므로 무시한다.
        }
    }

    /** dueDate가 오늘보다 이르면 OVERDUE, 오늘부터 7일 이내(양끝 포함)면 DUE_SOON, 그 외엔 해당 없음. */
    private Optional<NotificationType> classify(LocalDate dueDate, LocalDate today) {
        if (dueDate == null) {
            return Optional.empty();
        }
        if (dueDate.isBefore(today)) {
            return Optional.of(NotificationType.OVERDUE);
        }
        long daysUntilDue = ChronoUnit.DAYS.between(today, dueDate);
        if (daysUntilDue <= DUE_SOON_WINDOW_DAYS) {
            return Optional.of(NotificationType.DUE_SOON);
        }
        return Optional.empty();
    }

    private NotificationResDto toVirtualDto(ActionItem item, NotificationType type, Long userId, LocalDate today) {
        LocalDateTime dueDateTime = item.getDueDate().atStartOfDay();
        LocalDateTime readAt = null;
        if (type == NotificationType.DUE_SOON
                && notificationReadRepository.existsByIdUserIdAndIdDedupKeyAndIdReadDate(
                        userId, dueSoonDedupKey(item.getId()), today)) {
            readAt = today.atStartOfDay();
        }

        return new NotificationResDto(
                -item.getId(),
                userId,
                item.getProject().getId(),
                item.getId(),
                item.getTitle(),
                type,
                dueDateTime,
                dueDateTime,
                readAt
        );
    }

    private String dueSoonDedupKey(Long actionItemId) {
        return DUE_SOON_DEDUP_PREFIX + actionItemId;
    }

    private Notification getNotificationOrThrow(Long notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
    }

    private void requireOwner(Notification notification, Long userId) {
        if (!notification.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }
    }

    private void requireAssignee(ActionItem item, Long userId) {
        if (item.getAssignee() == null || !item.getAssignee().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }
    }
}
