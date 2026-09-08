package com.followup.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.followup.actionitem.entity.Priority;
import com.followup.ai.client.AiAnalysisClient;
import com.followup.ai.dto.AiDraftResult;
import com.followup.ai.dto.AiDraftResult.DraftActionItem;
import com.followup.ai.dto.AiDraftResult.DraftDecision;
import com.followup.ai.dto.AnalysisResponse;
import com.followup.ai.entity.AnalysisStatus;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.meeting.dto.MeetingCreateRequest;
import com.followup.meeting.dto.MeetingDetailResponse;
import com.followup.meeting.service.MeetingService;
import com.followup.project.dto.ProjectCreateRequest;
import com.followup.project.dto.ProjectResponse;
import com.followup.project.service.ProjectService;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
    private UserRepository userRepository;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @MockitoBean
    private AiAnalysisClient aiAnalysisClient;

    private Long ownerId;
    private Long outsiderId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        ownerId = userRepository.save(User.builder()
                .email("owner-" + suffix + "@test.com").password("pw").name("Owner").build()).getId();
        outsiderId = userRepository.save(User.builder()
                .email("outsider-" + suffix + "@test.com").password("pw").name("Outsider").build()).getId();
    }

    private void actingAs(Long userId) {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    private Long createProjectAsOwner() {
        actingAs(ownerId);
        ProjectResponse project = projectService.createProject(new ProjectCreateRequest("Project", null));
        return project.id();
    }

    private Long createMeetingWithContent(Long projectId, String content) {
        actingAs(ownerId);
        MeetingDetailResponse meeting = meetingService.createMeeting(projectId,
                new MeetingCreateRequest("Sync", LocalDateTime.of(2026, 9, 7, 10, 0), content, null, null));
        return meeting.id();
    }

    private AiDraftResult sampleDraft() {
        return new AiDraftResult(
                List.of(new DraftDecision("Decided to proceed with plan A")),
                List.of(new DraftActionItem("Write docs", "desc", "Owner",
                        LocalDate.of(2026, 9, 20), Priority.HIGH, "important"))
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
        AnalysisResponse response = result.response();

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
        AnalysisResponse response = aiAnalysisService.requestAnalysis(meetingId).response();

        assertThat(response.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(response.errorMessage()).isEqualTo("AI service unavailable");
        assertThat(response.draft()).isNull();
    }

    @Test
    void getAnalysis_success() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");
        when(aiAnalysisClient.analyze(any(), any())).thenReturn(sampleDraft());
        when(aiAnalysisClient.getModelName()).thenReturn("fake-model");
        when(aiAnalysisClient.getPromptVersion()).thenReturn("v1");
        actingAs(ownerId);
        AnalysisResponse created = aiAnalysisService.requestAnalysis(meetingId).response();

        AnalysisResponse fetched = aiAnalysisService.getAnalysis(created.id());

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
        AnalysisResponse created = aiAnalysisService.requestAnalysis(meetingId).response();

        actingAs(outsiderId);

        assertThatThrownBy(() -> aiAnalysisService.getAnalysis(created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);
    }
}
