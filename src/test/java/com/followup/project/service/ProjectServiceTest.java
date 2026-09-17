package com.followup.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.actionitem.repository.MeetingActionLinkRepository;
import com.followup.ai.entity.AiAnalysisRun;
import com.followup.ai.entity.AnalysisStatus;
import com.followup.ai.repository.AiAnalysisRunRepository;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.meeting.dto.MeetingCreateReqDto;
import com.followup.meeting.dto.MeetingDetailResDto;
import com.followup.meeting.entity.Decision;
import com.followup.meeting.repository.DecisionRepository;
import com.followup.meeting.repository.MeetingRepository;
import com.followup.meeting.service.MeetingService;
import com.followup.notification.entity.Notification;
import com.followup.notification.repository.NotificationRepository;
import com.followup.project.dto.ProjectCreateReqDto;
import com.followup.project.dto.ProjectResDto;
import com.followup.project.dto.ProjectUpdateReqDto;
import com.followup.project.entity.ProjectMember;
import com.followup.project.entity.ProjectRole;
import com.followup.project.repository.ProjectMemberRepository;
import com.followup.project.repository.ProjectRepository;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
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
class ProjectServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private ActionItemRepository actionItemRepository;

    @Autowired
    private DecisionRepository decisionRepository;

    @Autowired
    private AiAnalysisRunRepository aiAnalysisRunRepository;

    @Autowired
    private MeetingActionLinkRepository meetingActionLinkRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    private Long ownerId;
    private Long memberId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        ownerId = userRepository.save(User.builder()
                .email("owner-" + suffix + "@test.com")
                .password("pw")
                .name("Owner")
                .build()).getId();
        memberId = userRepository.save(User.builder()
                .email("member-" + suffix + "@test.com")
                .password("pw")
                .name("Member")
                .build()).getId();
    }

    private void actingAs(Long userId) {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    private void addMember(Long projectId, Long userId, ProjectRole role) {
        projectMemberRepository.save(ProjectMember.builder()
                .project(projectRepository.getReferenceById(projectId))
                .user(userRepository.getReferenceById(userId))
                .role(role)
                .build());
    }

    @Test
    void createProject_success() {
        actingAs(ownerId);

        ProjectResDto response = projectService.createProject(new ProjectCreateReqDto("Project A", "desc"));

        assertThat(response.name()).isEqualTo("Project A");
        assertThat(response.createdBy()).isEqualTo(ownerId);
    }

    @Test
    void createProject_registersOwnerMember() {
        actingAs(ownerId);

        ProjectResDto response = projectService.createProject(new ProjectCreateReqDto("Project A", null));

        assertThat(projectMemberRepository.existsByProjectIdAndUserId(response.id(), ownerId)).isTrue();
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(response.id(), ownerId).orElseThrow();
        assertThat(member.getRole()).isEqualTo(ProjectRole.OWNER);
    }

    @Test
    void getProjects_returnsOnlyMyProjects() {
        actingAs(ownerId);
        projectService.createProject(new ProjectCreateReqDto("Owner Project", null));

        actingAs(memberId);
        List<ProjectResDto> myProjects = projectService.getProjects();

        assertThat(myProjects).isEmpty();
    }

    @Test
    void getProject_success() {
        actingAs(ownerId);
        ProjectResDto created = projectService.createProject(new ProjectCreateReqDto("P", null));

        ProjectResDto fetched = projectService.getProject(created.id());

        assertThat(fetched.id()).isEqualTo(created.id());
    }

    @Test
    void getProject_nonMemberDenied() {
        actingAs(ownerId);
        ProjectResDto created = projectService.createProject(new ProjectCreateReqDto("P", null));

        actingAs(memberId);

        assertThatThrownBy(() -> projectService.getProject(created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);
    }

    @Test
    void getProject_notFound() {
        actingAs(ownerId);

        assertThatThrownBy(() -> projectService.getProject(9_999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
    }

    @Test
    void updateProject_ownerSuccess() {
        actingAs(ownerId);
        ProjectResDto created = projectService.createProject(new ProjectCreateReqDto("P", null));

        ProjectResDto updated = projectService.updateProject(created.id(), new ProjectUpdateReqDto("New name", null));

        assertThat(updated.name()).isEqualTo("New name");
    }

    @Test
    void updateProject_memberDenied() {
        actingAs(ownerId);
        ProjectResDto created = projectService.createProject(new ProjectCreateReqDto("P", null));
        addMember(created.id(), memberId, ProjectRole.MEMBER);

        actingAs(memberId);

        assertThatThrownBy(() -> projectService.updateProject(created.id(), new ProjectUpdateReqDto("x", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_OWNER_REQUIRED);
    }

    @Test
    void deleteProject_ownerSuccess() {
        actingAs(ownerId);
        ProjectResDto created = projectService.createProject(new ProjectCreateReqDto("P", null));

        projectService.deleteProject(created.id());

        assertThat(projectRepository.findById(created.id())).isEmpty();
    }

    /**
     * carry-over로 연결된 업무(meeting_action_links), AI 분석 이력을 origin/source로 참조하는 업무,
     * 그 업무에 걸린 알림(notifications)까지 모두 있는 프로젝트를 삭제해 FK 순서(notifications/meeting_action_links
     * -> action_items -> decisions -> ai_analysis_runs -> meetings)가 실제로 안전한지 검증한다.
     */
    @Test
    void deleteProject_cascadesMeetingsDecisionsAiRunsActionItemsAndNotifications() {
        actingAs(ownerId);
        ProjectResDto project = projectService.createProject(new ProjectCreateReqDto("P", null));
        Long projectId = project.id();

        ActionItem carryOver = actionItemRepository.save(ActionItem.builder()
                .project(projectRepository.getReferenceById(projectId))
                .title("Carry over task")
                .status(ActionItemStatus.TODO)
                .priority(Priority.MEDIUM)
                .build());

        MeetingDetailResDto meeting = meetingService.createMeeting(projectId,
                new MeetingCreateReqDto("Sync", LocalDateTime.of(2026, 9, 7, 10, 0), null, null,
                        List.of(carryOver.getId())));
        assertThat(meetingActionLinkRepository.findAllByMeetingId(meeting.id())).hasSize(1);

        Decision decision = decisionRepository.save(Decision.builder()
                .meeting(meetingRepository.getReferenceById(meeting.id()))
                .content("Decided")
                .build());

        AiAnalysisRun run = aiAnalysisRunRepository.save(AiAnalysisRun.builder()
                .meeting(meetingRepository.getReferenceById(meeting.id()))
                .requestedBy(userRepository.getReferenceById(ownerId))
                .status(AnalysisStatus.GENERATED)
                .build());

        ActionItem originated = actionItemRepository.save(ActionItem.builder()
                .project(projectRepository.getReferenceById(projectId))
                .originMeeting(meetingRepository.getReferenceById(meeting.id()))
                .sourceAnalysis(aiAnalysisRunRepository.getReferenceById(run.getId()))
                .title("From AI")
                .status(ActionItemStatus.TODO)
                .priority(Priority.MEDIUM)
                .build());

        notificationRepository.save(Notification.builder()
                .user(userRepository.getReferenceById(ownerId))
                .project(projectRepository.getReferenceById(projectId))
                .actionItem(actionItemRepository.getReferenceById(originated.getId()))
                .taskTitle("From AI")
                .remindAt(LocalDateTime.now().plusDays(1))
                .build());

        projectService.deleteProject(projectId);

        assertThat(projectRepository.findById(projectId)).isEmpty();
        assertThat(meetingRepository.findById(meeting.id())).isEmpty();
        assertThat(decisionRepository.findById(decision.getId())).isEmpty();
        assertThat(aiAnalysisRunRepository.findById(run.getId())).isEmpty();
        assertThat(actionItemRepository.findById(carryOver.getId())).isEmpty();
        assertThat(actionItemRepository.findById(originated.getId())).isEmpty();
        assertThat(meetingActionLinkRepository.findAllByMeetingId(meeting.id())).isEmpty();
        assertThat(notificationRepository.findByActionItemId(originated.getId())).isEmpty();
    }

    @Test
    void deleteProject_memberDenied() {
        actingAs(ownerId);
        ProjectResDto created = projectService.createProject(new ProjectCreateReqDto("P", null));
        addMember(created.id(), memberId, ProjectRole.MEMBER);

        actingAs(memberId);

        assertThatThrownBy(() -> projectService.deleteProject(created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_OWNER_REQUIRED);
    }
}
