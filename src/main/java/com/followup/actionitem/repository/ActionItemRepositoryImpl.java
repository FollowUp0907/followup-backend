package com.followup.actionitem.repository;

import static com.followup.actionitem.entity.QActionItem.actionItem;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import java.util.List;

public class ActionItemRepositoryImpl implements ActionItemRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public ActionItemRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public List<ActionItem> search(Long projectId, ActionItemStatus status, Long assigneeId, Priority priority) {
        return queryFactory
                .selectFrom(actionItem)
                .distinct()
                .where(
                        projectIdEq(projectId),
                        statusEq(status),
                        assigneeIdEq(assigneeId),
                        priorityEq(priority)
                )
                .orderBy(actionItem.createdAt.desc())
                .fetch();
    }

    @Override
    public List<ActionItem> findActiveByProjectId(Long projectId, Long assigneeId, Priority priority) {
        return queryFactory
                .selectFrom(actionItem)
                .distinct()
                .where(
                        projectIdEq(projectId),
                        actionItem.status.in(ActionItemStatus.TODO, ActionItemStatus.IN_PROGRESS),
                        assigneeIdEq(assigneeId),
                        priorityEq(priority)
                )
                .orderBy(actionItem.createdAt.desc())
                .fetch();
    }

    @Override
    public List<ActionItem> findAllByProjectIdFetchAssignees(Long projectId) {
        return queryFactory
                .selectFrom(actionItem)
                .distinct()
                .leftJoin(actionItem.assignees).fetchJoin()
                .where(projectIdEq(projectId))
                .fetch();
    }

    private BooleanExpression projectIdEq(Long projectId) {
        return actionItem.project.id.eq(projectId);
    }

    private BooleanExpression statusEq(ActionItemStatus status) {
        return status != null ? actionItem.status.eq(status) : null;
    }

    /** "내 업무"는 담당자 목록에 포함된 업무 전부를 뜻한다. */
    private BooleanExpression assigneeIdEq(Long assigneeId) {
        return assigneeId != null ? actionItem.assignees.any().id.eq(assigneeId) : null;
    }

    private BooleanExpression priorityEq(Priority priority) {
        return priority != null ? actionItem.priority.eq(priority) : null;
    }
}
