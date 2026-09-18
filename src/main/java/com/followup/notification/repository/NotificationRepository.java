package com.followup.notification.repository;

import com.followup.notification.entity.Notification;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByActionItemId(Long actionItemId);

    /** id를 2차 정렬키로 둬서, 같은 초에 몰려 생성된 알림(예: 업무 완료 시 여러 명에게 동시 발송)도 생성 순서대로 나온다. */
    List<Notification> findAllByUserIdOrderByRemindAtDescIdDesc(Long userId);

    List<Notification> findAllByUserIdAndRemindAtLessThanEqualOrderByRemindAtDescIdDesc(Long userId, LocalDateTime now);

    List<Notification> findAllByUserIdAndReadAtIsNullAndRemindAtLessThanEqual(Long userId, LocalDateTime now);

    /** unread-count의 "저장된 알림" 집계 — getNotifications()에서 readAt==null로 안 읽음을 판단하는 것과 동일한 조건이다. */
    long countByUserIdAndReadAtIsNull(Long userId);

    void deleteByActionItemId(Long actionItemId);

    void deleteAllByActionItemIdIn(List<Long> actionItemIds);
}
