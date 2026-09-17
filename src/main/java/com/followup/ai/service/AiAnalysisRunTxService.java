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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 분석 요청을 감싸는 짧고 독립적인 DB 트랜잭션들을 모은 클래스다.
 * 외부 Gemini 호출이 트랜잭션 밖에서 실행되도록 {@link AiAnalysisService}와 분리했다.
 */
@Service
@RequiredArgsConstructor
public class AiAnalysisRunTxService {

    // 재사용 가능한 상태 목록. FAILED는 제외해 항상 재시도되도록 한다.
    private static final List<AnalysisStatus> REUSABLE_STATUSES =
            List.of(AnalysisStatus.PROCESSING, AnalysisStatus.GENERATED, AnalysisStatus.CONFIRMED);

    private final AiAnalysisRunRepository aiAnalysisRunRepository;
    private final MeetingRepository meetingRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;

    /**
     * 같은 회의·입력 지문·모델/프롬프트 버전의 재사용 가능한 분석이 있으면 그대로 반환하고,
     * 없으면 새 PROCESSING row를 만든다. 변경되지 않은 회의록에 Gemini를 다시 호출하지 않기 위함이다.
     * (meeting_id, input_hash, model_name, prompt_version)에는 DB unique 제약이 없어(FAILED 재시도를
     * 허용해야 해서 단순 유니크 제약을 걸기 어렵다), 같은 회의에 대한 재사용 판단+생성 자체를
     * 비관적 쓰기 락으로 직렬화해 동시 요청이 똑같은 분석을 중복 생성/중복 호출하지 않게 막는다.
     * 이 트랜잭션은 재사용 판단과 row 생성까지만 담당하고, 실제 Gemini 호출은 이 락/트랜잭션 밖인
     * {@link AiAnalysisService#requestAnalysis}에서 이어서 수행된다.
     */
    @Transactional
    public AnalysisStart startAnalysis(Long meetingId, Long currentUserId, String modelName, String promptVersion) {
        Meeting meeting = meetingRepository.findByIdForUpdate(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        Long projectId = meeting.getProject().getId();
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, currentUserId)) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
        if (meeting.getContent() == null || meeting.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.MEETING_CONTENT_EMPTY);
        }

        String inputHash = computeInputHash(meeting.getContent(), meeting.getScheduledAt());

        Optional<AiAnalysisRun> existing = aiAnalysisRunRepository
                .findFirstByMeetingIdAndInputHashAndModelNameAndPromptVersionAndStatusInOrderByCreatedAtDesc(
                        meetingId, inputHash, modelName, promptVersion, REUSABLE_STATUSES);
        if (existing.isPresent()) {
            return AnalysisStart.reused(existing.get().getId());
        }

        AiAnalysisRun run = AiAnalysisRun.builder()
                .meeting(meeting)
                .requestedBy(userRepository.getReferenceById(currentUserId))
                .status(AnalysisStatus.PROCESSING)
                .modelName(modelName)
                .promptVersion(promptVersion)
                .inputHash(inputHash)
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

    /** Gemini 결과에 영향을 주는 content와 scheduledAt만으로 지문을 만든다. */
    private String computeInputHash(String content, LocalDateTime scheduledAt) {
        String normalizedScheduledAt = scheduledAt != null
                ? scheduledAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : "";
        String combined = content + "|" + normalizedScheduledAt;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
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
