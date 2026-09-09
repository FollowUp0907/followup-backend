package com.followup.actionitem.repository;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import java.util.List;

public interface ActionItemRepositoryCustom {

    List<ActionItem> search(Long projectId, ActionItemStatus status, Long assigneeId, Priority priority);

    List<ActionItem> findActiveByProjectId(Long projectId, Long assigneeId, Priority priority);

    /**
     * Dashboard 집계 시 assignee 접근으로 인한 N+1을 방지하기 위해
     * ActionItem과 assignee를 fetch join으로 함께 조회한다.
     */
    List<ActionItem> findAllByProjectIdFetchAssignee(Long projectId);
}
