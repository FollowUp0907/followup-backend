package com.followup.notification.repository;

import com.followup.notification.entity.NotificationRead;
import com.followup.notification.entity.NotificationReadId;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, NotificationReadId> {

    boolean existsByIdUserIdAndIdDedupKeyAndIdReadDate(Long userId, String dedupKey, LocalDate readDate);
}
