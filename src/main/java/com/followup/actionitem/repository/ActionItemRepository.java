package com.followup.actionitem.repository;

import com.followup.actionitem.entity.ActionItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionItemRepository extends JpaRepository<ActionItem, Long>, ActionItemRepositoryCustom {

    boolean existsByProjectId(Long projectId);

    List<ActionItem> findAllByProjectIdAndAssigneeId(Long projectId, Long assigneeId);

    List<ActionItem> findAllByOriginMeetingId(Long originMeetingId);

    void deleteAllByProjectId(Long projectId);
}
