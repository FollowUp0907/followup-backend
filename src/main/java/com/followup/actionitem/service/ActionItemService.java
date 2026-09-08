package com.followup.actionitem.service;

import com.followup.actionitem.dto.ActionItemCreateRequest;
import com.followup.actionitem.dto.ActionItemDetailResponse;
import com.followup.actionitem.dto.ActionItemListResponse;
import com.followup.actionitem.dto.ActionItemUpdateRequest;
import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.actionitem.repository.MeetingActionLinkRepository;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.project.entity.Project;
import com.followup.project.repository.ProjectMemberRepository;
import com.followup.project.repository.ProjectRepository;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
    private final CurrentUserProvider currentUserProvider;

    /** {@code status=active}는 TODO + IN_PROGRESS를 뜻하는 가상 필터다({@link ActionItemStatus} 값 아님). */
    @Transactional(readOnly = true)
    public List<ActionItemListResponse> getActionItems(Long projectId, String status, Long assigneeId, Priority priority) {
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

        return actionItems.stream().map(ActionItemListResponse::from).toList();
    }

    @Transactional
    public ActionItemDetailResponse createActionItem(Long projectId, ActionItemCreateRequest request) {
        Project project = getProjectOrThrow(projectId);
        requireMember(projectId, currentUserProvider.getCurrentUserId());

        User assignee = resolveAssignee(projectId, request.assigneeUserId());

        ActionItem actionItem = ActionItem.builder()
                .project(project)
                .originMeeting(null)
                .assignee(assignee)
                .sourceAnalysis(null)
                .title(request.title())
                .description(request.description())
                .dueDate(request.dueDate())
                .status(ActionItemStatus.TODO)
                .priority(request.priority() != null ? request.priority() : Priority.MEDIUM)
                .priorityReason(null)
                .completedAt(null)
                .build();
        actionItemRepository.save(actionItem);

        return ActionItemDetailResponse.from(actionItem);
    }

    @Transactional(readOnly = true)
    public ActionItemDetailResponse getActionItem(Long actionItemId) {
        ActionItem actionItem = getActionItemOrThrow(actionItemId);
        requireMember(actionItem.getProject().getId(), currentUserProvider.getCurrentUserId());
        return ActionItemDetailResponse.from(actionItem);
    }

    @Transactional
    public ActionItemDetailResponse updateActionItem(Long actionItemId, ActionItemUpdateRequest request) {
        ActionItem actionItem = getActionItemOrThrow(actionItemId);
        Long projectId = actionItem.getProject().getId();
        requireMember(projectId, currentUserProvider.getCurrentUserId());

        if (request.title() != null && request.title().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        actionItem.update(request.title(), request.description(), request.dueDate(), request.priority());

        if (request.assigneeUserId() != null) {
            actionItem.assignTo(resolveAssignee(projectId, request.assigneeUserId()));
        }

        if (request.status() != null) {
            actionItem.changeStatus(request.status());
        }

        return ActionItemDetailResponse.from(actionItem);
    }

    /** MeetingActionLink만 제거하고 연결된 Meeting은 유지한다. */
    @Transactional
    public void deleteActionItem(Long actionItemId) {
        ActionItem actionItem = getActionItemOrThrow(actionItemId);
        requireMember(actionItem.getProject().getId(), currentUserProvider.getCurrentUserId());

        meetingActionLinkRepository.deleteAllByActionItemId(actionItemId);
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
