package com.followup.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.ai.client.AiAnalysisClient;
import com.followup.ai.dto.AiDraftResultDto;
import com.followup.ai.dto.AiDraftResultDto.DraftActionItem;
import com.followup.ai.dto.AiDraftResultDto.DraftDecision;
import com.followup.ai.dto.AnalysisConfirmReqDto;
import com.followup.ai.dto.AnalysisConfirmReqDto.ActionItemConfirmItem;
import com.followup.ai.dto.AnalysisConfirmReqDto.DecisionConfirmItem;
import com.followup.ai.dto.AnalysisResDto;
import com.followup.ai.entity.AiAnalysisRun;
import com.followup.ai.entity.AnalysisStatus;
import com.followup.ai.repository.AiAnalysisRunRepository;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.meeting.dto.MeetingCreateReqDto;
import com.followup.meeting.dto.MeetingDetailResDto;
import com.followup.meeting.dto.MeetingUpdateReqDto;
import com.followup.meeting.entity.Decision;
import com.followup.meeting.repository.DecisionRepository;
import com.followup.meeting.repository.MeetingRepository;
import com.followup.meeting.service.MeetingService;
import com.followup.project.dto.ProjectCreateReqDto;
import com.followup.project.dto.ProjectMemberCreateReqDto;
import com.followup.project.dto.ProjectResDto;
import com.followup.project.service.ProjectMemberService;
import com.followup.project.service.ProjectService;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AiAnalysisServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private AiAnalysisService aiAnalysisService;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DecisionRepository decisionRepository;

    @Autowired
    private ActionItemRepository actionItemRepository;

    @Autowired
    private AiAnalysisRunRepository aiAnalysisRunRepository;

    @Autowired
    private AiAnalysisRunTxService aiAnalysisRunTransactionService;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @MockitoBean
    private AiAnalysisClient aiAnalysisClient;

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

    private Long createMeetingWithContent(Long projectId, String content) {
        actingAs(ownerId);
        MeetingDetailResDto meeting = meetingService.createMeeting(projectId,
                new MeetingCreateReqDto("Sync", LocalDateTime.of(2026, 9, 7, 10, 0), content, null, null));
        return meeting.id();
    }

    private AiDraftResultDto sampleDraft() {
        return new AiDraftResultDto(
                List.of(new DraftDecision("Decided to proceed with plan A")),
                List.of(new DraftActionItem("Write docs", "desc", "Owner",
                        LocalDate.of(2026, 9, 20), Priority.HIGH, "important"))
        );
    }

    private AnalysisResDto generateAnalysis(Long meetingId) {
        when(aiAnalysisClient.analyze(any(), any())).thenReturn(sampleDraft());
        when(aiAnalysisClient.getModelName()).thenReturn("fake-model");
        when(aiAnalysisClient.getPromptVersion()).thenReturn("v1");
        actingAs(ownerId);
        return aiAnalysisService.requestAnalysis(meetingId).response();
    }

    private AnalysisConfirmReqDto confirmRequest(Long assigneeUserId, Priority priority) {
        return new AnalysisConfirmReqDto(
                List.of(new DecisionConfirmItem("다음 스프린트에서 알림 기능을 우선 개발한다.")),
                List.of(new ActionItemConfirmItem("알림 API 설계", "알림 생성 및 조회 API 설계",
                        assigneeUserId, LocalDate.of(2026, 9, 15), priority, "다음 스프린트 핵심 기능"))
        );
    }

    @Test
    void requestAnalysis_success() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "We discussed the roadmap.");
        when(aiAnalysisClient.analyze(any(), any())).thenReturn(sampleDraft());
        when(aiAnalysisClient.getModelName()).thenReturn("fake-model");
        when(aiAnalysisClient.getPromptVersion()).thenReturn("v1");

        actingAs(ownerId);
        AiAnalysisService.AnalysisRequestResult result = aiAnalysisService.requestAnalysis(meetingId);
        AnalysisResDto response = result.response();

        assertThat(result.reused()).isFalse();
        assertThat(response.status()).isEqualTo(AnalysisStatus.GENERATED);
        assertThat(response.modelName()).isEqualTo("fake-model");
        assertThat(response.promptVersion()).isEqualTo("v1");
        assertThat(response.draft().decisions()).hasSize(1);
        assertThat(response.draft().actionItems()).hasSize(1);
        assertThat(response.errorMessage()).isNull();
    }

    @Test
    void requestAnalysis_passesMeetingScheduledAtToClient() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "We discussed the roadmap.");
        when(aiAnalysisClient.analyze(any(), any())).thenReturn(sampleDraft());
        when(aiAnalysisClient.getModelName()).thenReturn("fake-model");
        when(aiAnalysisClient.getPromptVersion()).thenReturn("v1");

        actingAs(ownerId);
        aiAnalysisService.requestAnalysis(meetingId);

        ArgumentCaptor<LocalDateTime> scheduledAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(aiAnalysisClient).analyze(any(), scheduledAtCaptor.capture());
        assertThat(scheduledAtCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 7, 10, 0));
    }

    @Test
    void requestAnalysis_meetingNotFound() {
        actingAs(ownerId);

        assertThatThrownBy(() -> aiAnalysisService.requestAnalysis(9_999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEETING_NOT_FOUND);
    }

    @Test
    void requestAnalysis_nonMemberDenied() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");

        actingAs(outsiderId);

        assertThatThrownBy(() -> aiAnalysisService.requestAnalysis(meetingId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);
    }

    @Test
    void requestAnalysis_emptyMeetingContentRejected() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "   ");

        actingAs(ownerId);

        assertThatThrownBy(() -> aiAnalysisService.requestAnalysis(meetingId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEETING_CONTENT_EMPTY);
    }

    @Test
    void requestAnalysis_aiFailureRecordsFailedStatus() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");
        when(aiAnalysisClient.analyze(any(), any())).thenThrow(new RuntimeException("AI service unavailable"));

        actingAs(ownerId);
        AnalysisResDto response = aiAnalysisService.requestAnalysis(meetingId).response();

        assertThat(response.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(response.errorMessage()).isEqualTo("AI service unavailable");
        assertThat(response.draft()).isNull();
    }

    @Test
    void requestAnalysis_duplicateRequestDoesNotCallGeminiAgain() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "We discussed the roadmap.");
        when(aiAnalysisClient.analyze(any(), any())).thenReturn(sampleDraft());
        when(aiAnalysisClient.getModelName()).thenReturn("fake-model");
        when(aiAnalysisClient.getPromptVersion()).thenReturn("v1");
        actingAs(ownerId);

        aiAnalysisService.requestAnalysis(meetingId);
        aiAnalysisService.requestAnalysis(meetingId);

        verify(aiAnalysisClient, times(1)).analyze(any(), any());
    }

    @Test
    void requestAnalysis_reusesGeneratedAnalysisForSameInput() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "We discussed the roadmap.");
        when(aiAnalysisClient.analyze(any(), any())).thenReturn(sampleDraft());
        when(aiAnalysisClient.getModelName()).thenReturn("fake-model");
        when(aiAnalysisClient.getPromptVersion()).thenReturn("v1");
        actingAs(ownerId);
        AiAnalysisService.AnalysisRequestResult first = aiAnalysisService.requestAnalysis(meetingId);
        assertThat(first.response().status()).isEqualTo(AnalysisStatus.GENERATED);

        AiAnalysisService.AnalysisRequestResult second = aiAnalysisService.requestAnalysis(meetingId);

        assertThat(second.reused()).isTrue();
        assertThat(second.response().id()).isEqualTo(first.response().id());
        assertThat(second.response().status()).isEqualTo(AnalysisStatus.GENERATED);
    }

    @Test
    void requestAnalysis_reusesConfirmedAnalysisForSameInput() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "We discussed the roadmap.");
        AnalysisResDto generated = generateAnalysis(meetingId);
        actingAs(ownerId);
        aiAnalysisService.confirmAnalysis(generated.id(), confirmRequest(null, Priority.HIGH));

        AiAnalysisService.AnalysisRequestResult result = aiAnalysisService.requestAnalysis(meetingId);

        assertThat(result.reused()).isTrue();
        assertThat(result.response().id()).isEqualTo(generated.id());
        assertThat(result.response().status()).isEqualTo(AnalysisStatus.CONFIRMED);
        // Only the one Gemini call made by generateAnalysis(); confirming/re-requesting never call it again.
        verify(aiAnalysisClient, times(1)).analyze(any(), any());
    }

    @Test
    void requestAnalysis_reusesProcessingAnalysisForSameInputWithoutCallingGemini() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "We discussed the roadmap.");
        when(aiAnalysisClient.getModelName()).thenReturn("fake-model");
        when(aiAnalysisClient.getPromptVersion()).thenReturn("v1");
        actingAs(ownerId);
        // Seed a PROCESSING record the same way AiAnalysisService itself would, without ever
        // resolving it -- simulates a request that is still in flight.
        AiAnalysisRunTxService.AnalysisStart processing =
                aiAnalysisRunTransactionService.startAnalysis(meetingId, ownerId, "fake-model", "v1");

        AiAnalysisService.AnalysisRequestResult result = aiAnalysisService.requestAnalysis(meetingId);

        assertThat(result.reused()).isTrue();
        assertThat(result.response().id()).isEqualTo(processing.analysisId());
        assertThat(result.response().status()).isEqualTo(AnalysisStatus.PROCESSING);
        verify(aiAnalysisClient, never()).analyze(any(), any());
    }

    @Test
    void requestAnalysis_failedAnalysisAllowsGeminiRetry() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "We discussed the roadmap.");
        when(aiAnalysisClient.getModelName()).thenReturn("fake-model");
        when(aiAnalysisClient.getPromptVersion()).thenReturn("v1");
        when(aiAnalysisClient.analyze(any(), any())).thenThrow(new RuntimeException("AI service unavailable"));
        actingAs(ownerId);
        AiAnalysisService.AnalysisRequestResult first = aiAnalysisService.requestAnalysis(meetingId);
        assertThat(first.response().status()).isEqualTo(AnalysisStatus.FAILED);

        // doReturn(...).when(...) (not when(...).thenReturn(...)) -- the mock is still stubbed to
        // throw, and when(mock.method()) would re-invoke it to record the call, immediately
        // re-triggering that throw before the new stub could be applied.
        doReturn(sampleDraft()).when(aiAnalysisClient).analyze(any(), any());
        AiAnalysisService.AnalysisRequestResult second = aiAnalysisService.requestAnalysis(meetingId);

        assertThat(second.reused()).isFalse();
        assertThat(second.response().id()).isNotEqualTo(first.response().id());
        assertThat(second.response().status()).isEqualTo(AnalysisStatus.GENERATED);
        verify(aiAnalysisClient, times(2)).analyze(any(), any());
    }

    @Test
    void requestAnalysis_newAnalysisWhenMeetingContentChanges() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Original content");
        when(aiAnalysisClient.analyze(any(), any())).thenReturn(sampleDraft());
        when(aiAnalysisClient.getModelName()).thenReturn("fake-model");
        when(aiAnalysisClient.getPromptVersion()).thenReturn("v1");
        actingAs(ownerId);
        AiAnalysisService.AnalysisRequestResult first = aiAnalysisService.requestAnalysis(meetingId);

        meetingService.updateMeeting(meetingId, new MeetingUpdateReqDto(null, null, "Updated content", null));
        AiAnalysisService.AnalysisRequestResult second = aiAnalysisService.requestAnalysis(meetingId);

        assertThat(second.reused()).isFalse();
        assertThat(second.response().id()).isNotEqualTo(first.response().id());
        verify(aiAnalysisClient, times(2)).analyze(any(), any());
    }

    @Test
    void requestAnalysis_newAnalysisWhenScheduledAtChanges() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "We discussed the roadmap.");
        when(aiAnalysisClient.analyze(any(), any())).thenReturn(sampleDraft());
        when(aiAnalysisClient.getModelName()).thenReturn("fake-model");
        when(aiAnalysisClient.getPromptVersion()).thenReturn("v1");
        actingAs(ownerId);
        AiAnalysisService.AnalysisRequestResult first = aiAnalysisService.requestAnalysis(meetingId);

        meetingService.updateMeeting(meetingId,
                new MeetingUpdateReqDto(null, LocalDateTime.of(2026, 9, 8, 9, 0), null, null));
        AiAnalysisService.AnalysisRequestResult second = aiAnalysisService.requestAnalysis(meetingId);

        assertThat(second.reused()).isFalse();
        assertThat(second.response().id()).isNotEqualTo(first.response().id());
        verify(aiAnalysisClient, times(2)).analyze(any(), any());
    }

    @Test
    void requestAnalysis_newAnalysisWhenModelChanges() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "We discussed the roadmap.");
        when(aiAnalysisClient.analyze(any(), any())).thenReturn(sampleDraft());
        when(aiAnalysisClient.getPromptVersion()).thenReturn("v1");
        when(aiAnalysisClient.getModelName()).thenReturn("fake-model");
        actingAs(ownerId);
        AiAnalysisService.AnalysisRequestResult first = aiAnalysisService.requestAnalysis(meetingId);

        when(aiAnalysisClient.getModelName()).thenReturn("fake-model-v2");
        AiAnalysisService.AnalysisRequestResult second = aiAnalysisService.requestAnalysis(meetingId);

        assertThat(second.reused()).isFalse();
        assertThat(second.response().id()).isNotEqualTo(first.response().id());
        assertThat(second.response().modelName()).isEqualTo("fake-model-v2");
        verify(aiAnalysisClient, times(2)).analyze(any(), any());
    }

    @Test
    void requestAnalysis_newAnalysisWhenPromptVersionChanges() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "We discussed the roadmap.");
        when(aiAnalysisClient.analyze(any(), any())).thenReturn(sampleDraft());
        when(aiAnalysisClient.getModelName()).thenReturn("fake-model");
        when(aiAnalysisClient.getPromptVersion()).thenReturn("v1");
        actingAs(ownerId);
        AiAnalysisService.AnalysisRequestResult first = aiAnalysisService.requestAnalysis(meetingId);

        when(aiAnalysisClient.getPromptVersion()).thenReturn("v2");
        AiAnalysisService.AnalysisRequestResult second = aiAnalysisService.requestAnalysis(meetingId);

        assertThat(second.reused()).isFalse();
        assertThat(second.response().id()).isNotEqualTo(first.response().id());
        assertThat(second.response().promptVersion()).isEqualTo("v2");
        verify(aiAnalysisClient, times(2)).analyze(any(), any());
    }

    @Test
    void getAnalysis_success() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");
        when(aiAnalysisClient.analyze(any(), any())).thenReturn(sampleDraft());
        when(aiAnalysisClient.getModelName()).thenReturn("fake-model");
        when(aiAnalysisClient.getPromptVersion()).thenReturn("v1");
        actingAs(ownerId);
        AnalysisResDto created = aiAnalysisService.requestAnalysis(meetingId).response();

        AnalysisResDto fetched = aiAnalysisService.getAnalysis(created.id());

        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.meetingId()).isEqualTo(meetingId);
        assertThat(fetched.status()).isEqualTo(AnalysisStatus.GENERATED);
        assertThat(fetched.draft().decisions().get(0).content()).isEqualTo("Decided to proceed with plan A");
    }

    @Test
    void getAnalysis_notFound() {
        actingAs(ownerId);

        assertThatThrownBy(() -> aiAnalysisService.getAnalysis(9_999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_NOT_FOUND);
    }

    @Test
    void getAnalysis_nonMemberDenied() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");
        when(aiAnalysisClient.analyze(any(), any())).thenReturn(sampleDraft());
        when(aiAnalysisClient.getModelName()).thenReturn("fake-model");
        when(aiAnalysisClient.getPromptVersion()).thenReturn("v1");
        actingAs(ownerId);
        AnalysisResDto created = aiAnalysisService.requestAnalysis(meetingId).response();

        actingAs(outsiderId);

        assertThatThrownBy(() -> aiAnalysisService.getAnalysis(created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);
    }

    @Test
    void confirmAnalysis_success() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");
        AnalysisResDto generated = generateAnalysis(meetingId);

        actingAs(ownerId);
        AnalysisResDto confirmed = aiAnalysisService.confirmAnalysis(
                generated.id(), confirmRequest(null, Priority.HIGH));

        assertThat(confirmed.status()).isEqualTo(AnalysisStatus.CONFIRMED);
        assertThat(confirmed.confirmedAt()).isNotNull();
    }

    @Test
    void confirmAnalysis_createsDecisionLinkedToMeetingAndAnalysis() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");
        AnalysisResDto generated = generateAnalysis(meetingId);

        actingAs(ownerId);
        aiAnalysisService.confirmAnalysis(generated.id(), confirmRequest(null, Priority.HIGH));

        List<Decision> decisions = decisionRepository.findAllByMeetingId(meetingId);
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).getContent()).isEqualTo("다음 스프린트에서 알림 기능을 우선 개발한다.");
        assertThat(decisions.get(0).getMeeting().getId()).isEqualTo(meetingId);
        assertThat(decisions.get(0).getSourceAnalysis().getId()).isEqualTo(generated.id());
    }

    @Test
    void confirmAnalysis_createsActionItemWithOriginMeetingAndSourceAnalysis() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");
        AnalysisResDto generated = generateAnalysis(meetingId);

        actingAs(ownerId);
        aiAnalysisService.confirmAnalysis(generated.id(), confirmRequest(null, Priority.HIGH));

        List<ActionItem> actionItems = actionItemRepository.search(projectId, null, null, null);
        assertThat(actionItems).hasSize(1);
        ActionItem created = actionItems.get(0);
        assertThat(created.getTitle()).isEqualTo("알림 API 설계");
        assertThat(created.getOriginMeeting().getId()).isEqualTo(meetingId);
        assertThat(created.getSourceAnalysis().getId()).isEqualTo(generated.id());
        assertThat(created.getStatus()).isEqualTo(ActionItemStatus.TODO);
        assertThat(created.getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void confirmAnalysis_priorityNullDefaultsToMedium() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");
        AnalysisResDto generated = generateAnalysis(meetingId);

        actingAs(ownerId);
        aiAnalysisService.confirmAnalysis(generated.id(), confirmRequest(null, null));

        ActionItem created = actionItemRepository.search(projectId, null, null, null).get(0);
        assertThat(created.getPriority()).isEqualTo(Priority.MEDIUM);
    }

    @Test
    void confirmAnalysis_assigneeNullAllowed() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");
        AnalysisResDto generated = generateAnalysis(meetingId);

        actingAs(ownerId);
        aiAnalysisService.confirmAnalysis(generated.id(), confirmRequest(null, Priority.HIGH));

        ActionItem created = actionItemRepository.search(projectId, null, null, null).get(0);
        assertThat(created.getAssignee()).isNull();
    }

    @Test
    void confirmAnalysis_assignsValidProjectMember() {
        Long projectId = createProjectAsOwner();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));
        Long meetingId = createMeetingWithContent(projectId, "Some content");
        AnalysisResDto generated = generateAnalysis(meetingId);

        actingAs(ownerId);
        aiAnalysisService.confirmAnalysis(generated.id(), confirmRequest(memberId, Priority.HIGH));

        ActionItem created = actionItemRepository.search(projectId, null, memberId, null).get(0);
        assertThat(created.getAssignee().getId()).isEqualTo(memberId);
    }

    @Test
    void confirmAnalysis_nonMemberAssigneeRejected() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");
        AnalysisResDto generated = generateAnalysis(meetingId);

        actingAs(ownerId);

        assertThatThrownBy(() -> aiAnalysisService.confirmAnalysis(
                generated.id(), confirmRequest(outsiderId, Priority.HIGH)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ANALYSIS_ASSIGNEE);
    }

    @Test
    void confirmAnalysis_alreadyConfirmedRejected() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");
        AnalysisResDto generated = generateAnalysis(meetingId);
        actingAs(ownerId);
        aiAnalysisService.confirmAnalysis(generated.id(), confirmRequest(null, Priority.HIGH));

        assertThatThrownBy(() -> aiAnalysisService.confirmAnalysis(
                generated.id(), confirmRequest(null, Priority.HIGH)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_ALREADY_CONFIRMED);
    }

    @Test
    void confirmAnalysis_failedStatusRejected() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");
        when(aiAnalysisClient.analyze(any(), any())).thenThrow(new RuntimeException("boom"));
        actingAs(ownerId);
        AnalysisResDto failed = aiAnalysisService.requestAnalysis(meetingId).response();

        assertThatThrownBy(() -> aiAnalysisService.confirmAnalysis(
                failed.id(), confirmRequest(null, Priority.HIGH)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_NOT_CONFIRMABLE);
    }

    @Test
    void confirmAnalysis_processingStatusRejected() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");
        actingAs(ownerId);
        Long analysisId = createProcessingAnalysis(meetingId);

        assertThatThrownBy(() -> aiAnalysisService.confirmAnalysis(
                analysisId, confirmRequest(null, Priority.HIGH)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_NOT_CONFIRMABLE);
    }

    @Test
    void confirmAnalysis_rollsBackEverythingOnFailure() throws Exception {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");
        AnalysisResDto generated = generateAnalysis(meetingId);

        AnalysisConfirmReqDto request = new AnalysisConfirmReqDto(
                List.of(new DecisionConfirmItem("This decision should not survive")),
                List.of(new ActionItemConfirmItem("Invalid assignee item", null,
                        outsiderId, null, Priority.HIGH, null)));

        actingAs(ownerId);
        assertThatThrownBy(() -> aiAnalysisService.confirmAnalysis(generated.id(), request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ANALYSIS_ASSIGNEE);

        // The service call above shares the test's transaction, so reads through the same
        // JPA/JDBC resource would still see the not-yet-rolled-back rows. A fresh, unmanaged
        // JDBC connection uses its own transaction and MySQL's default isolation to confirm
        // that nothing was actually committed for the failed confirm attempt.
        assertThat(countRows("decisions", "meeting_id", meetingId)).isZero();
        assertThat(countRows("action_items", "project_id", projectId)).isZero();

        AnalysisResDto reloaded = aiAnalysisService.getAnalysis(generated.id());
        assertThat(reloaded.status()).isEqualTo(AnalysisStatus.GENERATED);
    }

    private int countRows(String table, String column, Long value) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    @Test
    void confirmAnalysis_nonMemberDenied() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");
        AnalysisResDto generated = generateAnalysis(meetingId);

        actingAs(outsiderId);

        assertThatThrownBy(() -> aiAnalysisService.confirmAnalysis(
                generated.id(), confirmRequest(null, Priority.HIGH)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);
    }

    @Test
    void confirmAnalysis_notFound() {
        actingAs(ownerId);

        assertThatThrownBy(() -> aiAnalysisService.confirmAnalysis(
                9_999_999L, confirmRequest(null, Priority.HIGH)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_NOT_FOUND);
    }

    private Long createProcessingAnalysis(Long meetingId) {
        // requestAnalysis() resolves PROCESSING -> GENERATED/FAILED synchronously via the fake client,
        // so a lingering PROCESSING record is built directly through the repository here.
        AiAnalysisRun run = AiAnalysisRun.builder()
                .meeting(meetingRepository.getReferenceById(meetingId))
                .requestedBy(userRepository.getReferenceById(ownerId))
                .status(AnalysisStatus.PROCESSING)
                .build();
        return aiAnalysisRunRepository.save(run).getId();
    }
}
