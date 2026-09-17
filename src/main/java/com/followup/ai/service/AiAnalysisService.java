package com.followup.ai.service;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import com.followup.actionitem.event.TaskAssignedEvent;
import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.ai.client.AiAnalysisClient;
import com.followup.ai.dto.AiDraftResultDto;
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
import com.followup.meeting.entity.Decision;
import com.followup.meeting.entity.Meeting;
import com.followup.meeting.repository.DecisionRepository;
import com.followup.project.entity.Project;
import com.followup.project.repository.ProjectMemberRepository;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 외부 Gemini 호출이 열린 DB 트랜잭션 안에서 실행되지 않도록, DB 작업은
 * {@link AiAnalysisRunTxService}의 짧은 트랜잭션으로 분리해 처리한다.
 * 재사용 가능한 이전 분석이 있으면 Gemini를 다시 호출하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final AiAnalysisRunRepository aiAnalysisRunRepository;
    private final DecisionRepository decisionRepository;
    private final ActionItemRepository actionItemRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final AiAnalysisClient aiAnalysisClient;
    private final AiAnalysisRunTxService aiAnalysisRunTxService;
    private final CurrentUserProvider currentUserProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public AnalysisRequestResult requestAnalysis(Long meetingId) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        String modelName = aiAnalysisClient.getModelName();
        String promptVersion = aiAnalysisClient.getPromptVersion();

        AiAnalysisRunTxService.AnalysisStart start =
                aiAnalysisRunTxService.startAnalysis(meetingId, currentUserId, modelName, promptVersion);

        if (!start.reused()) {
            try {
                AiDraftResultDto draft = aiAnalysisClient.analyze(start.meetingContent(), start.meetingScheduledAt());
                aiAnalysisRunTxService.completeWithSuccess(start.analysisId(), writeJson(draft));
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : ErrorCode.AI_ANALYSIS_FAILED.getMessage();
                aiAnalysisRunTxService.completeWithFailure(start.analysisId(), message);
            }
        }

        return new AnalysisRequestResult(getAnalysis(start.analysisId()), start.reused());
    }

    @Transactional(readOnly = true)
    public AnalysisResDto getAnalysis(Long analysisId) {
        AiAnalysisRun run = getAnalysisOrThrow(analysisId);
        requireMember(run.getMeeting().getProject().getId(), currentUserProvider.getCurrentUserId());

        return AnalysisResDto.of(run, readJson(run.getDraftJson()));
    }

    /** GENERATED 상태에서만 확정할 수 있고, 저장된 draftJson이 아니라 request body를 기준으로 생성한다. */
    @Transactional
    public AnalysisResDto confirmAnalysis(Long analysisId, AnalysisConfirmReqDto request) {
        AiAnalysisRun run = getAnalysisOrThrow(analysisId);
        Meeting meeting = run.getMeeting();
        Project project = meeting.getProject();
        Long actorId = currentUserProvider.getCurrentUserId();
        requireMember(project.getId(), actorId);

        if (run.getStatus() == AnalysisStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_CONFIRMED);
        }
        if (run.getStatus() != AnalysisStatus.GENERATED) {
            throw new BusinessException(ErrorCode.ANALYSIS_NOT_CONFIRMABLE);
        }

        for (DecisionConfirmItem item : orEmpty(request.decisions())) {
            decisionRepository.save(Decision.builder()
                    .meeting(meeting)
                    .content(item.content())
                    .sourceAnalysis(run)
                    .build());
        }

        for (ActionItemConfirmItem item : orEmpty(request.actionItems())) {
            User assignee = resolveAssignee(project.getId(), item.assigneeUserId());
            ActionItem actionItem = ActionItem.builder()
                    .project(project)
                    .originMeeting(meeting)
                    .assignee(assignee)
                    .sourceAnalysis(run)
                    .createdBy(userRepository.getReferenceById(actorId))
                    .title(item.title())
                    .description(item.description())
                    .dueDate(item.dueDate())
                    .status(ActionItemStatus.TODO)
                    .priority(item.priority() != null ? item.priority() : Priority.MEDIUM)
                    .priorityReason(item.priorityReason())
                    .build();
            actionItemRepository.save(actionItem);

            if (assignee != null && !actorId.equals(assignee.getId())) {
                eventPublisher.publishEvent(new TaskAssignedEvent(actionItem, actorId));
            }
        }

        run.confirm();

        return AnalysisResDto.of(run, readJson(run.getDraftJson()));
    }

    private String writeJson(AiDraftResultDto draft) {
        return objectMapper.writeValueAsString(draft);
    }

    private AiDraftResultDto readJson(String draftJson) {
        return draftJson != null ? objectMapper.readValue(draftJson, AiDraftResultDto.class) : null;
    }

    private <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private User resolveAssignee(Long projectId, Long assigneeUserId) {
        if (assigneeUserId == null) {
            return null;
        }
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, assigneeUserId)) {
            throw new BusinessException(ErrorCode.INVALID_ANALYSIS_ASSIGNEE);
        }
        return userRepository.getReferenceById(assigneeUserId);
    }

    private AiAnalysisRun getAnalysisOrThrow(Long analysisId) {
        return aiAnalysisRunRepository.findById(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_NOT_FOUND));
    }

    private void requireMember(Long projectId, Long userId) {
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
    }

    /** 200(재사용)과 201(신규 생성)을 구분하기 위한 내부 결과 타입이다. */
    public record AnalysisRequestResult(AnalysisResDto response, boolean reused) {
    }
}
