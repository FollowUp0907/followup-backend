package com.followup.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.dashboard.dto.DashboardResDto;
import com.followup.dashboard.dto.DashboardResDto.DueSoonActionItem;
import com.followup.dashboard.dto.DashboardResDto.MemberProgress;
import com.followup.dashboard.dto.DashboardResDto.RecentMeeting;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.meeting.entity.Meeting;
import com.followup.meeting.entity.MeetingStatus;
import com.followup.meeting.repository.MeetingRepository;
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
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** Dashboard 집계(요약/마감 임박/최근 회의/멤버 진행률)의 정책 경계 조건을 검증한다. */
@SpringBootTest
@Transactional
class DashboardServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ActionItemRepository actionItemRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    private Long ownerId;
    private Long outsiderId;
    private Long memberId;
    private String memberEmail;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        ownerId = userRepository.save(User.builder()
                .email("owner-" + suffix + "@test.com").password("pw").name("Owner").build()).getId();
        outsiderId = userRepository.save(User.builder()
                .email("outsider-" + suffix + "@test.com").password("pw").name("Outsider").build()).getId();
        memberEmail = "member-" + suffix + "@test.com";
        memberId = userRepository.save(User.builder()
                .email(memberEmail).password("pw").name("Member").build()).getId();
    }

    private void actingAs(Long userId) {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    private Long createProjectAsOwner() {
        actingAs(ownerId);
        ProjectResDto project = projectService.createProject(new ProjectCreateReqDto("Project", null));
        return project.id();
    }

    private void addExistingMember(Long projectId) {
        actingAs(ownerId);
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));
    }

    private ActionItem createActionItem(Long projectId, ActionItemStatus status, LocalDate dueDate, Long assigneeUserId) {
        ActionItem actionItem = actionItemRepository.save(ActionItem.builder()
                .project(projectRepository.getReferenceById(projectId))
                .title("Task")
                .status(status)
                .priority(Priority.MEDIUM)
                .dueDate(dueDate)
                .build());
        if (assigneeUserId != null) {
            actionItem.replaceAssignees(Set.of(userRepository.getReferenceById(assigneeUserId)));
        }
        return actionItem;
    }

    private Meeting createMeeting(Long projectId, LocalDateTime scheduledAt) {
        return meetingRepository.save(Meeting.builder()
                .project(projectRepository.getReferenceById(projectId))
                .title("Sync")
                .scheduledAt(scheduledAt)
                .status(MeetingStatus.DRAFT)
                .createdBy(userRepository.getReferenceById(ownerId))
                .build());
    }

    @Test
    void getDashboard_ownerSuccess() {
        Long projectId = createProjectAsOwner();

        actingAs(ownerId);
        DashboardResDto response = dashboardService.getDashboard(projectId);

        assertThat(response.actionItemSummary().total()).isZero();
        assertThat(response.dueSoonActionItems()).isEmpty();
        assertThat(response.recentMeetings()).isEmpty();
        assertThat(response.memberProgress()).hasSize(1);
    }

    @Test
    void getDashboard_memberSuccess() {
        Long projectId = createProjectAsOwner();
        addExistingMember(projectId);

        actingAs(memberId);
        DashboardResDto response = dashboardService.getDashboard(projectId);

        assertThat(response.memberProgress()).hasSize(2);
    }

    @Test
    void getDashboard_nonMemberDenied() {
        Long projectId = createProjectAsOwner();

        actingAs(outsiderId);

        assertThatThrownBy(() -> dashboardService.getDashboard(projectId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);
    }

    @Test
    void getDashboard_projectNotFound() {
        actingAs(ownerId);

        assertThatThrownBy(() -> dashboardService.getDashboard(9_999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
    }

    @Test
    void getDashboard_summaryCountsByStatus() {
        Long projectId = createProjectAsOwner();
        createActionItem(projectId, ActionItemStatus.TODO, null, null);
        createActionItem(projectId, ActionItemStatus.TODO, null, null);
        createActionItem(projectId, ActionItemStatus.IN_PROGRESS, null, null);
        createActionItem(projectId, ActionItemStatus.DONE, null, null);

        actingAs(ownerId);
        DashboardResDto response = dashboardService.getDashboard(projectId);

        assertThat(response.actionItemSummary().total()).isEqualTo(4);
        assertThat(response.actionItemSummary().todo()).isEqualTo(2);
        assertThat(response.actionItemSummary().inProgress()).isEqualTo(1);
        assertThat(response.actionItemSummary().done()).isEqualTo(1);
    }

    @Test
    void getDashboard_overdueIncludesYesterdayTodoOnly() {
        Long projectId = createProjectAsOwner();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        createActionItem(projectId, ActionItemStatus.TODO, yesterday, null);
        createActionItem(projectId, ActionItemStatus.DONE, yesterday, null);

        actingAs(ownerId);
        DashboardResDto response = dashboardService.getDashboard(projectId);

        assertThat(response.actionItemSummary().overdue()).isEqualTo(1);
    }

    @Test
    void getDashboard_dueSoonBoundariesAndOrdering() {
        Long projectId = createProjectAsOwner();
        LocalDate today = LocalDate.now();
        createActionItem(projectId, ActionItemStatus.TODO, today, null);
        createActionItem(projectId, ActionItemStatus.TODO, today.plusDays(3), null);
        createActionItem(projectId, ActionItemStatus.TODO, today.plusDays(4), null);
        createActionItem(projectId, ActionItemStatus.TODO, null, null);
        createActionItem(projectId, ActionItemStatus.DONE, today.plusDays(1), null);

        actingAs(ownerId);
        DashboardResDto response = dashboardService.getDashboard(projectId);

        List<DueSoonActionItem> dueSoon = response.dueSoonActionItems();
        assertThat(dueSoon).hasSize(2);
        assertThat(dueSoon.get(0).dueDate()).isEqualTo(today);
        assertThat(dueSoon.get(1).dueDate()).isEqualTo(today.plusDays(3));
    }

    @Test
    void getDashboard_dueSoonLimitedToFive() {
        Long projectId = createProjectAsOwner();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 6; i++) {
            createActionItem(projectId, ActionItemStatus.TODO, today.plusDays(i % 3), null);
        }

        actingAs(ownerId);
        DashboardResDto response = dashboardService.getDashboard(projectId);

        assertThat(response.dueSoonActionItems()).hasSize(5);
        List<LocalDate> dueDates = response.dueSoonActionItems().stream().map(DueSoonActionItem::dueDate).toList();
        assertThat(dueDates).isSorted();
    }

    @Test
    void getDashboard_recentMeetingsTop3DescByScheduledAt() {
        Long projectId = createProjectAsOwner();
        LocalDateTime base = LocalDateTime.of(2026, 9, 1, 10, 0);
        createMeeting(projectId, base);
        createMeeting(projectId, base.plusDays(1));
        createMeeting(projectId, base.plusDays(2));
        createMeeting(projectId, base.plusDays(3));

        actingAs(ownerId);
        DashboardResDto response = dashboardService.getDashboard(projectId);

        List<RecentMeeting> recentMeetings = response.recentMeetings();
        assertThat(recentMeetings).hasSize(3);
        assertThat(recentMeetings.get(0).scheduledAt()).isEqualTo(base.plusDays(3));
        assertThat(recentMeetings.get(1).scheduledAt()).isEqualTo(base.plusDays(2));
        assertThat(recentMeetings.get(2).scheduledAt()).isEqualTo(base.plusDays(1));
    }

    @Test
    void getDashboard_memberProgressIncludesAllMembersAndExcludesUnassigned() {
        Long projectId = createProjectAsOwner();
        addExistingMember(projectId);
        createActionItem(projectId, ActionItemStatus.DONE, null, ownerId);
        createActionItem(projectId, ActionItemStatus.TODO, null, ownerId);
        createActionItem(projectId, ActionItemStatus.TODO, null, null);

        actingAs(ownerId);
        DashboardResDto response = dashboardService.getDashboard(projectId);

        List<MemberProgress> progress = response.memberProgress();
        assertThat(progress).hasSize(2);

        MemberProgress ownerProgress = progress.stream()
                .filter(p -> p.userId().equals(ownerId)).findFirst().orElseThrow();
        assertThat(ownerProgress.totalCount()).isEqualTo(2);
        assertThat(ownerProgress.doneCount()).isEqualTo(1);
        assertThat(ownerProgress.completionRate()).isEqualTo(0.5);

        MemberProgress memberProgress = progress.stream()
                .filter(p -> p.userId().equals(memberId)).findFirst().orElseThrow();
        assertThat(memberProgress.totalCount()).isZero();
        assertThat(memberProgress.doneCount()).isZero();
        assertThat(memberProgress.completionRate()).isEqualTo(0.0);
    }

    /**
     * 담당자 여러 명이 걸린 업무도 각자의 진행률에 포함되어야 하고, assignees fetch join으로 인한
     * 행 늘어남 때문에 summary total이나 진행률이 중복 집계되면 안 된다.
     */
    @Test
    void getDashboard_memberProgressIncludesMultiAssigneeTaskWithoutDoubleCounting() {
        Long projectId = createProjectAsOwner();
        addExistingMember(projectId);
        ActionItem sharedTask = createActionItem(projectId, ActionItemStatus.TODO, null, ownerId);
        sharedTask.replaceAssignees(Set.of(
                userRepository.getReferenceById(ownerId),
                userRepository.getReferenceById(memberId)));
        actionItemRepository.save(sharedTask);

        actingAs(ownerId);
        DashboardResDto response = dashboardService.getDashboard(projectId);

        assertThat(response.actionItemSummary().total()).isEqualTo(1);

        List<MemberProgress> progress = response.memberProgress();
        MemberProgress ownerProgress = progress.stream()
                .filter(p -> p.userId().equals(ownerId)).findFirst().orElseThrow();
        assertThat(ownerProgress.totalCount()).isEqualTo(1);

        MemberProgress memberProgress = progress.stream()
                .filter(p -> p.userId().equals(memberId)).findFirst().orElseThrow();
        assertThat(memberProgress.totalCount()).isEqualTo(1);
    }

    @Test
    void getDashboard_doesNotMixOtherProjectData() {
        Long projectId = createProjectAsOwner();
        Long otherProjectId = createProjectAsOwner();
        createActionItem(otherProjectId, ActionItemStatus.TODO, LocalDate.now(), null);
        createMeeting(otherProjectId, LocalDateTime.now());

        actingAs(ownerId);
        DashboardResDto response = dashboardService.getDashboard(projectId);

        assertThat(response.actionItemSummary().total()).isZero();
        assertThat(response.dueSoonActionItems()).isEmpty();
        assertThat(response.recentMeetings()).isEmpty();
    }
}
