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

    /**
     * 프로젝트 cascade 삭제용 — action_item_id 기준 삭제(deleteAllByActionItemIdIn)로는 actionItemId가
     * null인 알림(업무와 무관한 알림)을 못 지우므로 project_id 기준으로 한 번 더 정리한다. 이미 지워진
     * 알림에 대해서는 그냥 0건 삭제되므로 중복 호출이어도 문제없다.
     */
    void deleteAllByProjectId(Long projectId);

    /** 회원 탈퇴 시 그 사람 앞으로 온 알림을 전부 지운다. */
    void deleteAllByUserId(Long userId);
}
