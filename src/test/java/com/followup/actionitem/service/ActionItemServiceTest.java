package com.followup.actionitem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.followup.actionitem.dto.ActionItemAssigneeResDto;
import com.followup.actionitem.dto.ActionItemCreateReqDto;
import com.followup.actionitem.dto.ActionItemDetailResDto;
import com.followup.actionitem.dto.ActionItemListResDto;
import com.followup.actionitem.dto.ActionItemUpdateReqDto;
import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.actionitem.repository.MeetingActionLinkRepository;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.meeting.dto.MeetingCreateReqDto;
import com.followup.meeting.dto.MeetingDetailResDto;
import com.followup.meeting.repository.MeetingRepository;
import com.followup.meeting.service.MeetingService;
import com.followup.project.dto.ProjectCreateReqDto;
import com.followup.project.dto.ProjectMemberCreateReqDto;
import com.followup.project.dto.ProjectResDto;
import com.followup.project.repository.ProjectRepository;
import com.followup.project.service.ProjectMemberService;
import com.followup.project.service.ProjectService;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ActionItemServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private ActionItemService actionItemService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActionItemRepository actionItemRepository;

    @Autowired
    private MeetingActionLinkRepository meetingActionLinkRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    private Long ownerId;
    private Long memberId;
    private String memberEmail;
    private Long outsiderId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        ownerId = userRepository.save(User.builder()
                .email("owner-" + suffix + "@test.com").password("pw").name("Owner").build()).getId();
        memberEmail = "member-" + suffix + "@test.com";
        memberId = userRepository.save(User.builder()
                .email(memberEmail).password("pw").name("Member").build()).getId();
        outsiderId = userRepository.save(User.builder()
                .email("outsider-" + suffix + "@test.com").password("pw").name("Outsider").build()).getId();
    }

    private void actingAs(Long userId) {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    private Long createProjectAsOwner() {
        actingAs(ownerId);
        ProjectResDto project = projectService.createProject(new ProjectCreateReqDto("Project", null));
        return project.id();
    }

    private ActionItemDetailResDto create(Long projectId, String title, Long assigneeUserId, Priority priority) {
        List<Long> assigneeUserIds = assigneeUserId != null ? List.of(assigneeUserId) : null;
        return actionItemService.createActionItem(projectId,
                new ActionItemCreateReqDto(title, null, assigneeUserIds, null, priority));
    }

    @Test
    void createActionItem_successWithDefaultTodoStatus() {
        Long projectId = createProjectAsOwner();

        ActionItemDetailResDto response = create(projectId, "Write docs", null, Priority.HIGH);

        assertThat(response.status()).isEqualTo(ActionItemStatus.TODO);
        assertThat(response.priority()).isEqualTo(Priority.HIGH);
        assertThat(response.originMeetingId()).isNull();
    }

    @Test
    void createActionItem_defaultPriorityIsMedium() {
        Long projectId = createProjectAsOwner();

        ActionItemDetailResDto response = create(projectId, "Write docs", null, null);

        assertThat(response.priority()).isEqualTo(Priority.MEDIUM);
    }

    @Test
    void createActionItem_nonMemberAssigneeRejected() {
        Long projectId = createProjectAsOwner();

        assertThatThrownBy(() -> create(projectId, "Task", outsiderId, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ACTION_ITEM_ASSIGNEE);
    }

    @Test
    void getActionItems_returnsAll() {
        Long projectId = createProjectAsOwner();
        create(projectId, "A", null, null);
        create(projectId, "B", null, null);

        List<ActionItemListResDto> result = actionItemService.getActionItems(projectId, null, null, null);

        assertThat(result).hasSize(2);
    }

    @Test
    void getActionItems_filterByStatusTodo() {
        Long projectId = createProjectAsOwner();
        ActionItemDetailResDto todo = create(projectId, "Todo item", null, null);
        ActionItemDetailResDto other = create(projectId, "Done item", null, null);
        actionItemService.updateActionItem(other.id(), new ActionItemUpdateReqDto(
                null, null, null, null, ActionItemStatus.DONE, null));

        List<ActionItemListResDto> result = actionItemService.getActionItems(projectId, "TODO", null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(todo.id());
    }

    @Test
    void getActionItems_filterByStatusInProgress() {
        Long projectId = createProjectAsOwner();
        ActionItemDetailResDto item = create(projectId, "Task", null, null);
        actionItemService.updateActionItem(item.id(), new ActionItemUpdateReqDto(
                null, null, null, null, ActionItemStatus.IN_PROGRESS, null));

        List<ActionItemListResDto> result = actionItemService.getActionItems(projectId, "IN_PROGRESS", null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(ActionItemStatus.IN_PROGRESS);
    }

    @Test
    void getActionItems_filterByPriority() {
        Long projectId = createProjectAsOwner();
        create(projectId, "High", null, Priority.HIGH);
        create(projectId, "Low", null, Priority.LOW);

        List<ActionItemListResDto> result = actionItemService.getActionItems(projectId, null, null, Priority.HIGH);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).priority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void getActionItems_filterByAssignee() {
        Long projectId = createProjectAsOwner();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));
        create(projectId, "Assigned", memberId, null);
        create(projectId, "Unassigned", null, null);

        List<ActionItemListResDto> result = actionItemService.getActionItems(projectId, null, memberId, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).assignees()).extracting(ActionItemAssigneeResDto::userId).containsExactly(memberId);
    }

    @Test
    void getActionItems_filterByAssignee_includesAnyOfMultipleAssignees() {
        Long projectId = createProjectAsOwner();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));
        actionItemService.createActionItem(projectId,
                new ActionItemCreateReqDto("Multi-assigned", null, List.of(ownerId, memberId), null, null));
        create(projectId, "Unrelated", null, null);

        List<ActionItemListResDto> result = actionItemService.getActionItems(projectId, null, memberId, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Multi-assigned");
    }

    @Test
    void getActionItems_combinedFilters() {
        Long projectId = createProjectAsOwner();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));
        ActionItemDetailResDto target = create(projectId, "Target", memberId, Priority.HIGH);
        actionItemService.updateActionItem(target.id(), new ActionItemUpdateReqDto(
                null, null, null, null, ActionItemStatus.IN_PROGRESS, null));
        create(projectId, "Other", memberId, Priority.LOW);

        List<ActionItemListResDto> result = actionItemService.getActionItems(
                projectId, "IN_PROGRESS", memberId, Priority.HIGH);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(target.id());
    }

    @Test
    void getActionItems_activeReturnsTodoAndInProgressOnly() {
        Long projectId = createProjectAsOwner();
        ActionItemDetailResDto todo = create(projectId, "Todo", null, null);
        ActionItemDetailResDto inProgress = create(projectId, "InProgress", null, null);
        actionItemService.updateActionItem(inProgress.id(), new ActionItemUpdateReqDto(
                null, null, null, null, ActionItemStatus.IN_PROGRESS, null));
        ActionItemDetailResDto done = create(projectId, "Done", null, null);
        actionItemService.updateActionItem(done.id(), new ActionItemUpdateReqDto(
                null, null, null, null, ActionItemStatus.DONE, null));

        List<ActionItemListResDto> result = actionItemService.getActionItems(projectId, "active", null, null);

        assertThat(result).extracting(ActionItemListResDto::id)
                .containsExactlyInAnyOrder(todo.id(), inProgress.id());
    }

    @Test
    void getActionItems_activeWithAssigneeFilter() {
        Long projectId = createProjectAsOwner();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));

        ActionItemDetailResDto memberTodo = create(projectId, "Member Todo", memberId, null);
        ActionItemDetailResDto memberInProgress = create(projectId, "Member InProgress", memberId, null);
        actionItemService.updateActionItem(memberInProgress.id(), new ActionItemUpdateReqDto(
                null, null, null, null, ActionItemStatus.IN_PROGRESS, null));
        create(projectId, "Unassigned Todo", null, null);

        List<ActionItemListResDto> result = actionItemService.getActionItems(projectId, "active", memberId, null);

        assertThat(result).extracting(ActionItemListResDto::id)
                .containsExactlyInAnyOrder(memberTodo.id(), memberInProgress.id());
    }

    @Test
    void getActionItems_activeWithPriorityFilter() {
        Long projectId = createProjectAsOwner();
        ActionItemDetailResDto highTodo = create(projectId, "High Todo", null, Priority.HIGH);
        ActionItemDetailResDto highInProgress = create(projectId, "High InProgress", null, Priority.HIGH);
        actionItemService.updateActionItem(highInProgress.id(), new ActionItemUpdateReqDto(
                null, null, null, null, ActionItemStatus.IN_PROGRESS, null));
        create(projectId, "Low Todo", null, Priority.LOW);

        List<ActionItemListResDto> result = actionItemService.getActionItems(projectId, "active", null, Priority.HIGH);

        assertThat(result).extracting(ActionItemListResDto::id)
                .containsExactlyInAnyOrder(highTodo.id(), highInProgress.id());
    }

    @Test
    void getActionItems_activeWithAssigneeAndPriorityFilter() {
        Long projectId = createProjectAsOwner();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));

        ActionItemDetailResDto target = create(projectId, "Target", memberId, Priority.HIGH);
        create(projectId, "Wrong priority", memberId, Priority.LOW);
        create(projectId, "Wrong assignee", null, Priority.HIGH);

        List<ActionItemListResDto> result = actionItemService.getActionItems(
                projectId, "active", memberId, Priority.HIGH);

        assertThat(result).extracting(ActionItemListResDto::id)
                .containsExactly(target.id());
    }

    @Test
    void getActionItems_activeExcludesDoneEvenWhenMatchingFilters() {
        Long projectId = createProjectAsOwner();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));

        ActionItemDetailResDto done = create(projectId, "Done matching filters", memberId, Priority.HIGH);
        actionItemService.updateActionItem(done.id(), new ActionItemUpdateReqDto(
                null, null, null, null, ActionItemStatus.DONE, null));

        List<ActionItemListResDto> result = actionItemService.getActionItems(
                projectId, "active", memberId, Priority.HIGH);

        assertThat(result).isEmpty();
    }

    @Test
    void getActionItems_invalidStatusRejected() {
        Long projectId = createProjectAsOwner();

        assertThatThrownBy(() -> actionItemService.getActionItems(projectId, "NOT_A_STATUS", null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ACTION_ITEM_STATUS);
    }

    @Test
    void getActionItem_success() {
        Long projectId = createProjectAsOwner();
        ActionItemDetailResDto created = create(projectId, "Task", null, null);

        ActionItemDetailResDto fetched = actionItemService.getActionItem(created.id());

        assertThat(fetched.id()).isEqualTo(created.id());
    }

    @Test
    void getActionItem_nonMemberDenied() {
        Long projectId = createProjectAsOwner();
        ActionItemDetailResDto created = create(projectId, "Task", null, null);

        actingAs(outsiderId);

        assertThatThrownBy(() -> actionItemService.getActionItem(created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);
    }

    @Test
    void updateActionItem_success() {
        Long projectId = createProjectAsOwner();
        ActionItemDetailResDto created = create(projectId, "Task", null, null);

        ActionItemDetailResDto updated = actionItemService.updateActionItem(created.id(),
                new ActionItemUpdateReqDto("Updated title", null, null, LocalDate.of(2026, 12, 25), null, null));

        assertThat(updated.title()).isEqualTo("Updated title");
        assertThat(updated.dueDate()).isEqualTo(LocalDate.of(2026, 12, 25));
    }

    @Test
    void updateActionItem_doneSetsCompletedAt() {
        Long projectId = createProjectAsOwner();
        ActionItemDetailResDto created = create(projectId, "Task", null, null);

        ActionItemDetailResDto updated = actionItemService.updateActionItem(created.id(),
                new ActionItemUpdateReqDto(null, null, null, null, ActionItemStatus.DONE, null));

        assertThat(updated.status()).isEqualTo(ActionItemStatus.DONE);
        assertThat(updated.completedAt()).isNotNull();
    }

    @Test
    void updateActionItem_backToInProgressClearsCompletedAt() {
        Long projectId = createProjectAsOwner();
        ActionItemDetailResDto created = create(projectId, "Task", null, null);
        actionItemService.updateActionItem(created.id(),
                new ActionItemUpdateReqDto(null, null, null, null, ActionItemStatus.DONE, null));

        ActionItemDetailResDto reverted = actionItemService.updateActionItem(created.id(),
                new ActionItemUpdateReqDto(null, null, null, null, ActionItemStatus.IN_PROGRESS, null));

        assertThat(reverted.completedAt()).isNull();
    }

    @Test
    void getActionItem_showsOriginMeetingTitleEvenAfterMeetingSoftDeleted() {
        Long projectId = createProjectAsOwner();
        MeetingDetailResDto meeting = meetingService.createMeeting(projectId,
                new MeetingCreateReqDto("Kickoff", LocalDateTime.of(2026, 9, 7, 10, 0), null, null, null));

        ActionItem item = actionItemRepository.save(ActionItem.builder()
                .project(projectRepository.getReferenceById(projectId))
                .originMeeting(meetingRepository.getReferenceById(meeting.id()))
                .title("From meeting")
                .status(ActionItemStatus.TODO)
                .priority(Priority.MEDIUM)
                .build());

        meetingService.deleteMeeting(meeting.id());

        ActionItemDetailResDto fetched = actionItemService.getActionItem(item.getId());

        assertThat(fetched.originMeetingId()).isEqualTo(meeting.id());
        assertThat(fetched.originMeetingTitle()).isEqualTo("Kickoff");
        assertThat(fetched.originMeetingDeleted()).isTrue();
    }

    @Test
    void deleteActionItem_success() {
        Long projectId = createProjectAsOwner();
        ActionItemDetailResDto created = create(projectId, "Task", null, null);

        actionItemService.deleteActionItem(created.id());

        assertThat(actionItemRepository.findById(created.id())).isEmpty();
    }

    @Test
    void deleteActionItem_removesMeetingActionLinkButKeepsMeeting() {
        Long projectId = createProjectAsOwner();
        ActionItemDetailResDto created = create(projectId, "Task", null, null);

        actionItemService.updateActionItem(created.id(), new ActionItemUpdateReqDto(
                null, null, null, null, ActionItemStatus.IN_PROGRESS, null));
        MeetingDetailResDto meeting = meetingService.createMeeting(projectId,
                new MeetingCreateReqDto("Sync", LocalDateTime.of(2026, 9, 7, 10, 0), null, null,
                        List.of(created.id())));
        assertThat(meetingActionLinkRepository.findAllByMeetingId(meeting.id())).hasSize(1);

        actionItemService.deleteActionItem(created.id());

        assertThat(meetingActionLinkRepository.findAllByMeetingId(meeting.id())).isEmpty();
        assertThat(meetingRepository.findById(meeting.id())).isPresent();
    }
}
