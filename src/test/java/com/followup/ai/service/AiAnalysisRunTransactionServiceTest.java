package com.followup.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.followup.ai.entity.AiAnalysisRun;
import com.followup.ai.entity.AnalysisStatus;
import com.followup.ai.repository.AiAnalysisRunRepository;
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
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Directly exercises AiAnalysisRunTransactionService's short PROCESSING -> GENERATED/FAILED
 * transactions, independent of the external AI call which lives in AiAnalysisService.
 */
@SpringBootTest
@Transactional
class AiAnalysisRunTransactionServiceTest {

    private static final String MODEL = "model-x";
    private static final String PROMPT_VERSION = "prompt-v9";

    @Autowired
    private ProjectService projectService;

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private AiAnalysisRunTransactionService aiAnalysisRunTransactionService;

    @Autowired
    private AiAnalysisRunRepository aiAnalysisRunRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

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

    private AiAnalysisRunTransactionService.AnalysisStart start(Long meetingId) {
        return aiAnalysisRunTransactionService.startAnalysis(meetingId, ownerId, MODEL, PROMPT_VERSION);
    }

    @Test
    void startAnalysis_createsProcessingRecordAndReturnsMeetingContent() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Meeting notes about the roadmap.");

        AiAnalysisRunTransactionService.AnalysisStart result = start(meetingId);

        assertThat(result.reused()).isFalse();
        assertThat(result.meetingContent()).isEqualTo("Meeting notes about the roadmap.");
        AiAnalysisRun run = aiAnalysisRunRepository.findById(result.analysisId()).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
    }

    @Test
    void startAnalysis_nonMemberDenied() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");

        assertThatThrownBy(() -> aiAnalysisRunTransactionService.startAnalysis(meetingId, outsiderId, MODEL, PROMPT_VERSION))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);
    }

    @Test
    void startAnalysis_emptyMeetingContentRejected() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "   ");

        assertThatThrownBy(() -> start(meetingId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEETING_CONTENT_EMPTY);
    }

    @Test
    void startAnalysis_recordPersistsBeforeCompletionPhaseRuns() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");

        AiAnalysisRunTransactionService.AnalysisStart result = start(meetingId);

        // Simulates the window between the short "start" transaction committing and the external
        // AI call resolving: the PROCESSING record must already be durably saved on its own.
        AiAnalysisRun run = aiAnalysisRunRepository.findById(result.analysisId()).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
        assertThat(run.getDraftJson()).isNull();
        assertThat(run.getErrorMessage()).isNull();
    }

    @Test
    void completeWithSuccess_transitionsProcessingToGenerated() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");
        AiAnalysisRunTransactionService.AnalysisStart result = start(meetingId);

        aiAnalysisRunTransactionService.completeWithSuccess(
                result.analysisId(), "{\"decisions\":[],\"actionItems\":[]}");

        AiAnalysisRun run = aiAnalysisRunRepository.findById(result.analysisId()).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(AnalysisStatus.GENERATED);
        assertThat(run.getDraftJson()).isEqualTo("{\"decisions\":[],\"actionItems\":[]}");
        assertThat(run.getModelName()).isEqualTo(MODEL);
        assertThat(run.getPromptVersion()).isEqualTo(PROMPT_VERSION);
    }

    @Test
    void completeWithFailure_transitionsProcessingToFailedWithErrorMessage() {
        Long projectId = createProjectAsOwner();
        Long meetingId = createMeetingWithContent(projectId, "Some content");
        AiAnalysisRunTransactionService.AnalysisStart result = start(meetingId);

        aiAnalysisRunTransactionService.completeWithFailure(result.analysisId(), "Gemini API request failed");

        AiAnalysisRun run = aiAnalysisRunRepository.findById(result.analysisId()).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo("Gemini API request failed");
        assertThat(run.getDraftJson()).isNull();
    }
}
