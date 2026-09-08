package com.followup.actionitem.repository;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import java.util.List;

public interface ActionItemRepositoryCustom {

    List<ActionItem> search(Long projectId, ActionItemStatus status, Long assigneeId, Priority priority);

    List<ActionItem> findActiveByProjectId(Long projectId, Long assigneeId, Priority priority);
}
