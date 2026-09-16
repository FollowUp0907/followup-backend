package com.followup.actionitem.repository;

import com.followup.actionitem.entity.ActionItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActionItemRepository extends JpaRepository<ActionItem, Long>, ActionItemRepositoryCustom {

    boolean existsByProjectId(Long projectId);

    List<ActionItem> findAllByProjectIdAndAssigneeId(Long projectId, Long assigneeId);

    List<ActionItem> findAllByOriginMeetingId(Long originMeetingId);

    void deleteAllByProjectId(Long projectId);

    /** 프로젝트 cascade 삭제(알림 등 하위 데이터 정리)용으로 id만 조회한다. */
    @Query("select a.id from ActionItem a where a.project.id = :projectId")
    List<Long> findIdsByProjectId(@Param("projectId") Long projectId);
}
