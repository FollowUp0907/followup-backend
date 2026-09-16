package com.followup.meeting.service;

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
import com.followup.meeting.dto.MeetingListResDto;
import com.followup.meeting.dto.MeetingUpdateReqDto;
import com.followup.meeting.entity.Decision;
import com.followup.meeting.entity.MeetingStatus;
import com.followup.meeting.repository.DecisionRepository;
import com.followup.meeting.repository.MeetingRepository;
import com.followup.project.dto.ProjectCreateReqDto;
import com.followup.project.dto.ProjectMemberCreateReqDto;
import com.followup.project.dto.ProjectResDto;
import com.followup.project.repository.ProjectRepository;
import com.followup.project.service.ProjectMemberService;
import com.followup.project.service.ProjectService;
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
class MeetingServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ActionItemRepository actionItemRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private MeetingActionLinkRepository meetingActionLinkRepository;

    @Autowired
    private AiAnalysisRunRepository aiAnalysisRunRepository;

    @Autowired
    private DecisionRepository decisionRepository;

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

    private ActionItem createActionItem(Long projectId, ActionItemStatus status) {
        return actionItemRepository.save(ActionItem.builder()
                .project(projectRepository.getReferenceById(projectId))
                .title("Task")
                .status(status)
                .priority(Priority.MEDIUM)
                .build());
    }

    private MeetingCreateReqDto baseRequest(List<Long> participantIds, List<Long> carryOverIds) {
        return new MeetingCreateReqDto("Weekly sync", LocalDateTime.of(2026, 9, 7, 14, 0), "notes",
                participantIds, carryOverIds);
    }

    @Test
    void createMeeting_successWithDraftStatusAndParticipants() {
        Long projectId = createProjectAsOwner();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));

        actingAs(ownerId);
        MeetingDetailResDto response = meetingService.createMeeting(
                projectId, baseRequest(List.of(memberId), null));

        assertThat(response.status()).isEqualTo(MeetingStatus.DRAFT);
        assertThat(response.participants()).hasSize(1);
        assertThat(response.participants().get(0).userId()).isEqualTo(memberId);
    }

    @Test
    void createMeeting_nonMemberDenied() {
        Long projectId = createProjectAsOwner();

        actingAs(outsiderId);

        assertThatThrownBy(() -> meetingService.createMeeting(projectId, baseRequest(null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);
    }

    @Test
    void createMeeting_invalidParticipantRejected() {
        Long projectId = createProjectAsOwner();

        actingAs(ownerId);

        assertThatThrownBy(() -> meetingService.createMeeting(projectId, baseRequest(List.of(outsiderId), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_MEETING_PARTICIPANT);
    }

    @Test
    void createMeeting_carryOverTodoSuccess() {
        Long projectId = createProjectAsOwner();
        ActionItem todo = createActionItem(projectId, ActionItemStatus.TODO);

        actingAs(ownerId);
        MeetingDetailResDto response = meetingService.createMeeting(
                projectId, baseRequest(null, List.of(todo.getId())));

        assertThat(response.carryOverActionItems()).hasSize(1);
        assertThat(response.carryOverActionItems().get(0).actionItemId()).isEqualTo(todo.getId());
    }

    @Test
    void createMeeting_carryOverInProgressSuccess() {
        Long projectId = createProjectAsOwner();
        ActionItem inProgress = createActionItem(projectId, ActionItemStatus.IN_PROGRESS);

        actingAs(ownerId);
        MeetingDetailResDto response = meetingService.createMeeting(
                projectId, baseRequest(null, List.of(inProgress.getId())));

        assertThat(response.carryOverActionItems()).hasSize(1);
    }

    @Test
    void createMeeting_carryOverDoneRejected() {
        Long projectId = createProjectAsOwner();
        ActionItem done = createActionItem(projectId, ActionItemStatus.DONE);

        actingAs(ownerId);

        assertThatThrownBy(() -> meetingService.createMeeting(projectId, baseRequest(null, List.of(done.getId()))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CARRY_OVER_ACTION_ITEM);
    }

    @Test
    void createMeeting_otherProjectActionItemRejected() {
        Long projectId = createProjectAsOwner();

        actingAs(ownerId);
        Long otherProjectId = projectService.createProject(new ProjectCreateReqDto("Other", null)).id();
        ActionItem otherProjectItem = createActionItem(otherProjectId, ActionItemStatus.TODO);

        assertThatThrownBy(() -> meetingService.createMeeting(
                projectId, baseRequest(null, List.of(otherProjectItem.getId()))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CARRY_OVER_ACTION_ITEM);
    }

    @Test
    void getMeetings_orderedByScheduledAtDesc() {
        Long projectId = createProjectAsOwner();
        actingAs(ownerId);
        meetingService.createMeeting(projectId,
                new MeetingCreateReqDto("Older", LocalDateTime.of(2026, 1, 1, 10, 0), null, null, null));
        meetingService.createMeeting(projectId,
                new MeetingCreateReqDto("Newer", LocalDateTime.of(2026, 6, 1, 10, 0), null, null, null));

        List<MeetingListResDto> meetings = meetingService.getMeetings(projectId);

        assertThat(meetings).hasSize(2);
        assertThat(meetings.get(0).title()).isEqualTo("Newer");
        assertThat(meetings.get(1).title()).isEqualTo("Older");
    }

    @Test
    void getMeeting_success() {
        Long projectId = createProjectAsOwner();
        actingAs(ownerId);
        MeetingDetailResDto created = meetingService.createMeeting(projectId, baseRequest(null, null));

        MeetingDetailResDto fetched = meetingService.getMeeting(created.id());

        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.projectId()).isEqualTo(projectId);
    }

    @Test
    void getMeeting_nonMemberDenied() {
        Long projectId = createProjectAsOwner();
        actingAs(ownerId);
        MeetingDetailResDto created = meetingService.createMeeting(projectId, baseRequest(null, null));

        actingAs(outsiderId);

        assertThatThrownBy(() -> meetingService.getMeeting(created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);
    }

    @Test
    void getMeeting_noDecisions_returnsEmptyList() {
        Long projectId = createProjectAsOwner();
        actingAs(ownerId);
        MeetingDetailResDto created = meetingService.createMeeting(projectId, baseRequest(null, null));

        MeetingDetailResDto fetched = meetingService.getMeeting(created.id());

        assertThat(fetched.decisions()).isEmpty();
    }

    @Test
    void getMeeting_returnsAllDecisionsInCreationOrder() {
        Long projectId = createProjectAsOwner();
        actingAs(ownerId);
        MeetingDetailResDto created = meetingService.createMeeting(projectId, baseRequest(null, null));
        Decision first = decisionRepository.save(Decision.builder()
                .meeting(meetingRepository.getReferenceById(created.id())).content("Decision A").build());
        Decision second = decisionRepository.save(Decision.builder()
                .meeting(meetingRepository.getReferenceById(created.id())).content("Decision B").build());

        MeetingDetailResDto fetched = meetingService.getMeeting(created.id());

        assertThat(fetched.decisions()).hasSize(2);
        assertThat(fetched.decisions().get(0).id()).isEqualTo(first.getId());
        assertThat(fetched.decisions().get(0).content()).isEqualTo("Decision A");
        assertThat(fetched.decisions().get(1).id()).isEqualTo(second.getId());
        assertThat(fetched.decisions().get(1).content()).isEqualTo("Decision B");
    }

    @Test
    void getMeeting_doesNotMixDecisionsFromOtherMeetings() {
        Long projectId = createProjectAsOwner();
        actingAs(ownerId);
        MeetingDetailResDto meetingA = meetingService.createMeeting(projectId, baseRequest(null, null));
        MeetingDetailResDto meetingB = meetingService.createMeeting(projectId, baseRequest(null, null));
        decisionRepository.save(Decision.builder()
                .meeting(meetingRepository.getReferenceById(meetingB.id())).content("Only for B").build());

        MeetingDetailResDto fetchedA = meetingService.getMeeting(meetingA.id());

        assertThat(fetchedA.decisions()).isEmpty();
    }

    @Test
    void updateMeeting_success() {
        Long projectId = createProjectAsOwner();
        actingAs(ownerId);
        MeetingDetailResDto created = meetingService.createMeeting(projectId, baseRequest(null, null));

        MeetingDetailResDto updated = meetingService.updateMeeting(created.id(),
                new MeetingUpdateReqDto("Updated title", null, null, null));

        assertThat(updated.title()).isEqualTo("Updated title");
    }

    @Test
    void updateMeeting_replacesParticipants() {
        Long projectId = createProjectAsOwner();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));

        actingAs(ownerId);
        MeetingDetailResDto created = meetingService.createMeeting(projectId, baseRequest(List.of(memberId), null));
        assertThat(created.participants()).hasSize(1);

        MeetingDetailResDto updated = meetingService.updateMeeting(created.id(),
                new MeetingUpdateReqDto(null, null, null, List.of(ownerId)));

        assertThat(updated.participants()).hasSize(1);
        assertThat(updated.participants().get(0).userId()).isEqualTo(ownerId);
    }

    @Test
    void deleteMeeting_softDeletesButKeepsRow() {
        Long projectId = createProjectAsOwner();
        actingAs(ownerId);
        MeetingDetailResDto created = meetingService.createMeeting(projectId, baseRequest(null, null));

        meetingService.deleteMeeting(created.id());

        assertThat(meetingRepository.findById(created.id())).isPresent();
        assertThat(meetingRepository.findById(created.id()).get().getDeletedAt()).isNotNull();
    }

    @Test
    void deleteMeeting_excludedFromListAndGetThrowsNotFound() {
        Long projectId = createProjectAsOwner();
        actingAs(ownerId);
        MeetingDetailResDto created = meetingService.createMeeting(projectId, baseRequest(null, null));

        meetingService.deleteMeeting(created.id());

        assertThat(meetingService.getMeetings(projectId)).isEmpty();
        assertThatThrownBy(() -> meetingService.getMeeting(created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEETING_NOT_FOUND);
    }

    @Test
    void deleteMeeting_keepsActionItemAndLink() {
        Long projectId = createProjectAsOwner();
        ActionItem todo = createActionItem(projectId, ActionItemStatus.TODO);

        actingAs(ownerId);
        MeetingDetailResDto created = meetingService.createMeeting(
                projectId, baseRequest(null, List.of(todo.getId())));
        assertThat(meetingActionLinkRepository.findAllByMeetingId(created.id())).hasSize(1);

        meetingService.deleteMeeting(created.id());

        assertThat(actionItemRepository.findById(todo.getId())).isPresent();
        assertThat(meetingActionLinkRepository.findAllByMeetingId(created.id())).hasSize(1);
    }

    @Test
    void deleteMeeting_succeedsAndKeepsHistoryWhenAiAnalysisRunExists() {
        Long projectId = createProjectAsOwner();
        actingAs(ownerId);
        MeetingDetailResDto created = meetingService.createMeeting(projectId, baseRequest(null, null));
        AiAnalysisRun run = aiAnalysisRunRepository.save(AiAnalysisRun.builder()
                .meeting(meetingRepository.getReferenceById(created.id()))
                .requestedBy(userRepository.getReferenceById(ownerId))
                .status(AnalysisStatus.GENERATED)
                .build());

        meetingService.deleteMeeting(created.id());

        assertThat(meetingRepository.findById(created.id())).isPresent();
        assertThat(aiAnalysisRunRepository.findById(run.getId())).isPresent();
    }

    @Test
    void deleteMeeting_succeedsAndKeepsHistoryWhenDecisionExists() {
        Long projectId = createProjectAsOwner();
        actingAs(ownerId);
        MeetingDetailResDto created = meetingService.createMeeting(projectId, baseRequest(null, null));
        Decision decision = decisionRepository.save(Decision.builder()
                .meeting(meetingRepository.getReferenceById(created.id()))
                .content("Decided to proceed")
                .build());

        meetingService.deleteMeeting(created.id());

        assertThat(meetingRepository.findById(created.id())).isPresent();
        assertThat(decisionRepository.findById(decision.getId())).isPresent();
    }
}
