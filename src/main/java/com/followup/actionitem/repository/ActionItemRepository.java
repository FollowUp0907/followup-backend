package com.followup.actionitem.repository;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActionItemRepository extends JpaRepository<ActionItem, Long>, ActionItemRepositoryCustom {

    boolean existsByProjectId(Long projectId);

    /**
     * OVERDUE/DUE_SOON 가상 알림 계산용 — 담당자로 배정된 업무 중 완료되지 않았고 마감일이 있는 것만
     * 대상으로 한다. "exists (select 1 from a.assignees ...)"는 조인으로 인한 행 늘어남 없이 존재
     * 여부만 확인하는 서브쿼리로 컴파일되지만, distinct로 한 번 더 안전장치를 둔다.
     */
    @Query("""
            select distinct a from ActionItem a
            where exists (select 1 from a.assignees ca where ca.id = :userId)
              and a.status <> :status
              and a.dueDate is not null
            """)
    List<ActionItem> findAllAssignedWithDueDate(@Param("userId") Long userId, @Param("status") ActionItemStatus status);

    /** unread-count의 OVERDUE 집계 — findAllAssignedWithDueDate의 isOverdue 부분과 정확히 같은 조건이어야 한다. */
    @Query("""
            select count(distinct a) from ActionItem a
            where exists (select 1 from a.assignees ca where ca.id = :userId)
              and a.status <> :status
              and a.dueDate < :today
            """)
    long countOverdueAssigned(@Param("userId") Long userId, @Param("status") ActionItemStatus status,
                              @Param("today") LocalDate today);

    /**
     * unread-count의 DUE_SOON 집계 — NotificationService의 isDueSoon 판정(담당자/미완료/오늘<=dueDate<=
     * dueSoonLimit)과 동일한 범위를 쓰고, 그중 오늘자 notification_reads 표시가 없는 것만 센다(NOT EXISTS로
     * 목록 없이 개수만 계산).
     */
    @Query("""
            select count(distinct a) from ActionItem a
            where exists (select 1 from a.assignees ca where ca.id = :userId)
              and a.status <> :done
              and a.dueDate is not null
              and a.dueDate >= :today
              and a.dueDate <= :dueSoonLimit
              and not exists (
                  select 1 from NotificationRead nr
                  where nr.id.userId = :userId
                    and nr.id.dedupKey = concat(:dedupPrefix, str(a.id))
                    and nr.id.readDate = :today
              )
            """)
    long countDueSoonUnread(@Param("userId") Long userId, @Param("done") ActionItemStatus done,
                            @Param("today") LocalDate today, @Param("dueSoonLimit") LocalDate dueSoonLimit,
                            @Param("dedupPrefix") String dedupPrefix);

    /** 프로젝트 멤버 제거 시, 그 사람이 담당자로 걸려 있는 업무를 찾기 위한 조회다. */
    List<ActionItem> findAllByProjectIdAndAssigneesId(Long projectId, Long userId);

    List<ActionItem> findAllByOriginMeetingId(Long originMeetingId);

    void deleteAllByProjectId(Long projectId);

    /** 프로젝트 cascade 삭제(알림 등 하위 데이터 정리)용으로 id만 조회한다. */
    @Query("select a.id from ActionItem a where a.project.id = :projectId")
    List<Long> findIdsByProjectId(@Param("projectId") Long projectId);

    /**
     * deleteAllByProjectId는 벌크 삭제라 엔티티를 거치지 않아 담당자 매핑 테이블이 함께 지워지지
     * 않는다(FK 위반 방지용). action_items를 지우기 전에 먼저 호출해야 한다.
     */
    @Modifying
    @Query(value = "delete from action_item_assignees where action_item_id in (:actionItemIds)", nativeQuery = true)
    void deleteAllAssigneesByActionItemIdIn(@Param("actionItemIds") List<Long> actionItemIds);

    /**
     * 회원 탈퇴 시 만든 사람 참조만 끊는다 — OWNER가 아니었던(=cascade 삭제 대상이 아닌) 프로젝트에 남아
     * 있는 업무의 created_by가 대상이다. fk_action_items_created_by는 nullable이지만 RESTRICT라서
     * 미리 끊지 않으면 회원 삭제 시 FK 위반이 난다.
     * flushAutomatically로 직전의 담당자 제거(removeAssignee, 엔티티 dirty-check)가 먼저 반영되게 하고,
     * clearAutomatically로 영속성 컨텍스트를 비워 이후 조회가 벌크 업데이트 전 캐시된 값이 아니라
     * 갱신된 값을 보게 한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ActionItem a set a.createdBy = null where a.createdBy.id = :userId")
    void detachCreator(@Param("userId") Long userId);
}
