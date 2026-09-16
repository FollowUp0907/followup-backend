package com.followup.notification.repository;

import com.followup.notification.entity.Notification;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByActionItemId(Long actionItemId);

    List<Notification> findAllByUserIdOrderByRemindAtDesc(Long userId);

    List<Notification> findAllByUserIdAndRemindAtLessThanEqualOrderByRemindAtDesc(Long userId, LocalDateTime now);

    List<Notification> findAllByUserIdAndReadAtIsNullAndRemindAtLessThanEqual(Long userId, LocalDateTime now);

    void deleteByActionItemId(Long actionItemId);

    void deleteAllByActionItemIdIn(List<Long> actionItemIds);
}
