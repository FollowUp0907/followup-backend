package com.followup.ai.service;

import com.followup.ai.entity.AiAnalysisRun;
import com.followup.ai.entity.AnalysisStatus;
import com.followup.ai.repository.AiAnalysisRunRepository;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.meeting.entity.Meeting;
import com.followup.meeting.repository.MeetingRepository;
import com.followup.project.repository.ProjectMemberRepository;
import com.followup.user.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 분석 요청을 감싸는 짧고 독립적인 DB 트랜잭션들을 모은 클래스다.
 * 외부 Gemini 호출이 트랜잭션 밖에서 실행되도록 {@link AiAnalysisService}와 분리했다.
 */
@Service
@RequiredArgsConstructor
public class AiAnalysisRunTransactionService {

    private final AiAnalysisRunRepository aiAnalysisRunRepository;
    private final MeetingRepository meetingRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public AnalysisStart startAnalysis(Long meetingId, Long currentUserId, String modelName, String promptVersion) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        Long projectId = meeting.getProject().getId();
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, currentUserId)) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
        if (meeting.getContent() == null || meeting.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.MEETING_CONTENT_EMPTY);
        }

        AiAnalysisRun run = AiAnalysisRun.builder()
                .meeting(meeting)
                .requestedBy(userRepository.getReferenceById(currentUserId))
                .status(AnalysisStatus.PROCESSING)
                .modelName(modelName)
                .promptVersion(promptVersion)
                .build();
        aiAnalysisRunRepository.save(run);

        return AnalysisStart.started(run.getId(), meeting.getContent(), meeting.getScheduledAt());
    }

    @Transactional
    public void completeWithSuccess(Long analysisId, String draftJson) {
        getOrThrow(analysisId).markGenerated(draftJson);
    }

    @Transactional
    public void completeWithFailure(Long analysisId, String errorMessage) {
        getOrThrow(analysisId).markFailed(errorMessage);
    }

    private AiAnalysisRun getOrThrow(Long analysisId) {
        return aiAnalysisRunRepository.findById(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_NOT_FOUND));
    }

    public record AnalysisStart(Long analysisId, String meetingContent, LocalDateTime meetingScheduledAt,
                                 boolean reused) {

        public static AnalysisStart started(Long analysisId, String meetingContent, LocalDateTime meetingScheduledAt) {
            return new AnalysisStart(analysisId, meetingContent, meetingScheduledAt, false);
        }

        public static AnalysisStart reused(Long analysisId) {
            return new AnalysisStart(analysisId, null, null, true);
        }
    }
}
