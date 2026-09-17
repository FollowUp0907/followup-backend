package com.followup.actionitem.service;

import com.followup.actionitem.dto.ActionItemCreateReqDto;
import com.followup.actionitem.dto.ActionItemDetailResDto;
import com.followup.actionitem.dto.ActionItemListResDto;
import com.followup.actionitem.dto.ActionItemUpdateReqDto;
import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import com.followup.actionitem.event.TaskAssignedEvent;
import com.followup.actionitem.event.TaskCompletedEvent;
import com.followup.actionitem.event.TaskUpdatedEvent;
import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.actionitem.repository.MeetingActionLinkRepository;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.notification.repository.NotificationRepository;
import com.followup.project.entity.Project;
import com.followup.project.repository.ProjectMemberRepository;
import com.followup.project.repository.ProjectRepository;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActionItemService {

    private static final String ACTIVE_STATUS_PARAM = "active";

    private final ActionItemRepository actionItemRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final MeetingActionLinkRepository meetingActionLinkRepository;
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CurrentUserProvider currentUserProvider;

    /** {@code status=active}는 TODO + IN_PROGRESS를 뜻하는 가상 필터다({@link ActionItemStatus} 값 아님). */
    @Transactional(readOnly = true)
    public List<ActionItemListResDto> getActionItems(Long projectId, String status, Long assigneeId, Priority priority) {
        getProjectOrThrow(projectId);
        requireMember(projectId, currentUserProvider.getCurrentUserId());

        List<ActionItem> actionItems;
        if (status == null) {
            actionItems = actionItemRepository.search(projectId, null, assigneeId, priority);
        } else if (status.equalsIgnoreCase(ACTIVE_STATUS_PARAM)) {
            actionItems = actionItemRepository.findActiveByProjectId(projectId, assigneeId, priority);
        } else {
            actionItems = actionItemRepository.search(projectId, parseStatus(status), assigneeId, priority);
        }

        return actionItems.stream().map(ActionItemListResDto::from).toList();
    }

    @Transactional
    public ActionItemDetailResDto createActionItem(Long projectId, ActionItemCreateReqDto request) {
        Project project = getProjectOrThrow(projectId);
        Long actorId = currentUserProvider.getCurrentUserId();
        requireMember(projectId, actorId);

        User assignee = resolveAssignee(projectId, request.assigneeUserId());

        ActionItem actionItem = ActionItem.builder()
                .project(project)
                .originMeeting(null)
                .assignee(assignee)
                .sourceAnalysis(null)
                .createdBy(userRepository.getReferenceById(actorId))
                .title(request.title())
                .description(request.description())
                .dueDate(request.dueDate())
                .status(ActionItemStatus.TODO)
                .priority(request.priority() != null ? request.priority() : Priority.MEDIUM)
                .priorityReason(null)
                .completedAt(null)
                .build();
        actionItemRepository.save(actionItem);

        if (assignee != null && !actorId.equals(assignee.getId())) {
            eventPublisher.publishEvent(new TaskAssignedEvent(actionItem, actorId));
        }

        return ActionItemDetailResDto.from(actionItem);
    }

    @Transactional(readOnly = true)
    public ActionItemDetailResDto getActionItem(Long actionItemId) {
        ActionItem actionItem = getActionItemOrThrow(actionItemId);
        requireMember(actionItem.getProject().getId(), currentUserProvider.getCurrentUserId());
        return ActionItemDetailResDto.from(actionItem);
    }

    @Transactional
    public ActionItemDetailResDto updateActionItem(Long actionItemId, ActionItemUpdateReqDto request) {
        ActionItem actionItem = getActionItemOrThrow(actionItemId);
        Long projectId = actionItem.getProject().getId();
        Long actorId = currentUserProvider.getCurrentUserId();
        requireMember(projectId, actorId);

        if (request.title() != null && request.title().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        User prevAssignee = actionItem.getAssignee();
        ActionItemStatus prevStatus = actionItem.getStatus();
        boolean fieldsChanged = isChanged(request.title(), actionItem.getTitle())
                || isChanged(request.description(), actionItem.getDescription())
                || isChanged(request.dueDate(), actionItem.getDueDate())
                || isChanged(request.priority(), actionItem.getPriority());

        actionItem.update(request.title(), request.description(), request.dueDate(), request.priority());

        User newAssignee = null;
        boolean assigneeChanged = false;
        if (request.assigneeUserId() != null) {
            newAssignee = resolveAssignee(projectId, request.assigneeUserId());
            Long prevAssigneeId = prevAssignee != null ? prevAssignee.getId() : null;
            assigneeChanged = !newAssignee.getId().equals(prevAssigneeId);
            actionItem.assignTo(newAssignee);
        }

        if (request.status() != null) {
            actionItem.changeStatus(request.status());
        }

        publishUpdateEvents(actionItem, actorId, prevAssignee, prevStatus, assigneeChanged, newAssignee, fieldsChanged);

        return ActionItemDetailResDto.from(actionItem);
    }

    /**
     * 담당자 변경이 우선이라, TaskAssignedEvent가 발행되는 경우엔 TaskUpdatedEvent를 같이 발행하지 않는다.
     * status만 바뀐 경우(칸반 드래그)는 fieldsChanged가 false이므로 TaskUpdatedEvent 대상이 아니다.
     */
    private void publishUpdateEvents(ActionItem actionItem, Long actorId, User prevAssignee, ActionItemStatus prevStatus,
                                      boolean assigneeChanged, User newAssignee, boolean fieldsChanged) {
        if (assigneeChanged && !actorId.equals(newAssignee.getId())) {
            eventPublisher.publishEvent(new TaskAssignedEvent(actionItem, actorId));
        } else if (fieldsChanged) {
            Long prevAssigneeId = prevAssignee != null ? prevAssignee.getId() : null;
            Long currentAssigneeId = actionItem.getAssignee() != null ? actionItem.getAssignee().getId() : null;
            boolean actorIsAssignee = actorId.equals(prevAssigneeId) || actorId.equals(currentAssigneeId);
            if (!actorIsAssignee) {
                eventPublisher.publishEvent(new TaskUpdatedEvent(actionItem, actorId));
            }
        }

        if (prevStatus != ActionItemStatus.DONE && actionItem.getStatus() == ActionItemStatus.DONE) {
            eventPublisher.publishEvent(new TaskCompletedEvent(actionItem, actorId));
        }
    }

    private boolean isChanged(Object requestValue, Object currentValue) {
        return requestValue != null && !requestValue.equals(currentValue);
    }

    /** MeetingActionLink만 제거하고 연결된 Meeting은 유지한다. */
    @Transactional
    public void deleteActionItem(Long actionItemId) {
        ActionItem actionItem = getActionItemOrThrow(actionItemId);
        requireMember(actionItem.getProject().getId(), currentUserProvider.getCurrentUserId());

        meetingActionLinkRepository.deleteAllByActionItemId(actionItemId);
        notificationRepository.deleteByActionItemId(actionItemId);
        actionItemRepository.delete(actionItem);
    }

    private ActionItemStatus parseStatus(String status) {
        try {
            return ActionItemStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_ACTION_ITEM_STATUS);
        }
    }

    /** 담당자는 같은 프로젝트 멤버만 지정할 수 있다. */
    private User resolveAssignee(Long projectId, Long assigneeUserId) {
        if (assigneeUserId == null) {
            return null;
        }
        User user = userRepository.findById(assigneeUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, assigneeUserId)) {
            throw new BusinessException(ErrorCode.INVALID_ACTION_ITEM_ASSIGNEE);
        }
        return user;
    }

    private Project getProjectOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private ActionItem getActionItemOrThrow(Long actionItemId) {
        return actionItemRepository.findById(actionItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTION_ITEM_NOT_FOUND));
    }

    private void requireMember(Long projectId, Long userId) {
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
    }
}
