package com.followup.meeting.service;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.actionitem.entity.MeetingActionLink;
import com.followup.actionitem.repository.MeetingActionLinkRepository;
import com.followup.ai.repository.AiAnalysisRunRepository;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.meeting.dto.MeetingCreateRequest;
import com.followup.meeting.dto.MeetingDetailResponse;
import com.followup.meeting.dto.MeetingListResponse;
import com.followup.meeting.dto.MeetingUpdateRequest;
import com.followup.meeting.entity.Meeting;
import com.followup.meeting.entity.MeetingParticipant;
import com.followup.meeting.entity.MeetingStatus;
import com.followup.meeting.repository.DecisionRepository;
import com.followup.meeting.repository.MeetingParticipantRepository;
import com.followup.meeting.repository.MeetingRepository;
import com.followup.project.entity.Project;
import com.followup.project.repository.ProjectMemberRepository;
import com.followup.project.repository.ProjectRepository;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private static final String CARRY_OVER_LINK_TYPE = "CARRY_OVER";

    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final MeetingActionLinkRepository meetingActionLinkRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final ActionItemRepository actionItemRepository;
    private final AiAnalysisRunRepository aiAnalysisRunRepository;
    private final DecisionRepository decisionRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public MeetingDetailResponse createMeeting(Long projectId, MeetingCreateRequest request) {
        Project project = getProjectOrThrow(projectId);
        requireMember(projectId, currentUserProvider.getCurrentUserId());

        List<Long> participantIds = distinct(request.participantIds());
        validateParticipants(projectId, participantIds);

        List<ActionItem> carryOverItems = resolveCarryOverActionItems(projectId, distinct(request.carryOverActionItemIds()));

        User creator = userRepository.getReferenceById(currentUserProvider.getCurrentUserId());
        Meeting meeting = Meeting.builder()
                .project(project)
                .title(request.title())
                .scheduledAt(request.scheduledAt())
                .content(request.content())
                .status(MeetingStatus.DRAFT)
                .createdBy(creator)
                .build();
        meetingRepository.save(meeting);

        List<MeetingParticipant> participants = participantIds.stream()
                .map(userId -> MeetingParticipant.builder()
                        .meeting(meeting)
                        .user(userRepository.getReferenceById(userId))
                        .build())
                .toList();
        meetingParticipantRepository.saveAll(participants);

        carryOverItems.forEach(actionItem -> meetingActionLinkRepository.save(MeetingActionLink.builder()
                .meeting(meeting)
                .actionItem(actionItem)
                .linkType(CARRY_OVER_LINK_TYPE)
                .build()));

        return MeetingDetailResponse.from(meeting, participants, carryOverItems);
    }

    @Transactional(readOnly = true)
    public List<MeetingListResponse> getMeetings(Long projectId) {
        getProjectOrThrow(projectId);
        requireMember(projectId, currentUserProvider.getCurrentUserId());

        return meetingRepository.findAllByProjectIdOrderByScheduledAtDesc(projectId).stream()
                .map(MeetingListResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MeetingDetailResponse getMeeting(Long meetingId) {
        Meeting meeting = getMeetingOrThrow(meetingId);
        requireMember(meeting.getProject().getId(), currentUserProvider.getCurrentUserId());

        List<MeetingParticipant> participants = meetingParticipantRepository.findAllByMeetingId(meetingId);
        List<ActionItem> carryOverItems = meetingActionLinkRepository.findAllByMeetingId(meetingId).stream()
                .map(MeetingActionLink::getActionItem)
                .toList();

        return MeetingDetailResponse.from(meeting, participants, carryOverItems);
    }

    @Transactional
    public MeetingDetailResponse updateMeeting(Long meetingId, MeetingUpdateRequest request) {
        Meeting meeting = getMeetingOrThrow(meetingId);
        Long projectId = meeting.getProject().getId();
        requireMember(projectId, currentUserProvider.getCurrentUserId());

        if (request.title() != null && request.title().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        meeting.update(request.title(), request.scheduledAt(), request.content());

        List<MeetingParticipant> participants;
        if (request.participantIds() != null) {
            List<Long> newParticipantIds = distinct(request.participantIds());
            validateParticipants(projectId, newParticipantIds);

            meetingParticipantRepository.deleteAllByMeetingId(meetingId);
            participants = newParticipantIds.stream()
                    .map(userId -> MeetingParticipant.builder()
                            .meeting(meeting)
                            .user(userRepository.getReferenceById(userId))
                            .build())
                    .toList();
            meetingParticipantRepository.saveAll(participants);
        } else {
            participants = meetingParticipantRepository.findAllByMeetingId(meetingId);
        }

        List<ActionItem> carryOverItems = meetingActionLinkRepository.findAllByMeetingId(meetingId).stream()
                .map(MeetingActionLink::getActionItem)
                .toList();

        return MeetingDetailResponse.from(meeting, participants, carryOverItems);
    }

    /**
     * AiAnalysisRun/Decision 이력이 있으면 409로 막아 확정된 이력을 보존한다.
     * ActionItem은 삭제하지 않고 originMeeting 연결만 해제한다.
     */
    @Transactional
    public void deleteMeeting(Long meetingId) {
        Meeting meeting = getMeetingOrThrow(meetingId);
        requireMember(meeting.getProject().getId(), currentUserProvider.getCurrentUserId());

        if (aiAnalysisRunRepository.existsByMeetingId(meetingId) || decisionRepository.existsByMeetingId(meetingId)) {
            throw new BusinessException(ErrorCode.MEETING_DELETE_CONFLICT);
        }

        meetingActionLinkRepository.deleteAllByMeetingId(meetingId);
        meetingParticipantRepository.deleteAllByMeetingId(meetingId);
        actionItemRepository.findAllByOriginMeetingId(meetingId)
                .forEach(ActionItem::detachOriginMeeting);

        meetingRepository.delete(meeting);
    }

    private List<Long> distinct(List<Long> ids) {
        return ids == null ? List.of() : ids.stream().distinct().toList();
    }

    /** participant는 프로젝트 멤버만 지정할 수 있다. */
    private void validateParticipants(Long projectId, List<Long> participantIds) {
        for (Long userId : participantIds) {
            if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
                throw new BusinessException(ErrorCode.INVALID_MEETING_PARTICIPANT);
            }
        }
    }

    /** 사용자가 선택한 TODO/IN_PROGRESS ActionItem만 carryOver로 연결하며, 미완료 업무 전체를 자동으로 끌어오지 않는다. */
    private List<ActionItem> resolveCarryOverActionItems(Long projectId, List<Long> actionItemIds) {
        if (actionItemIds.isEmpty()) {
            return List.of();
        }

        List<ActionItem> actionItems = actionItemRepository.findAllById(actionItemIds);
        if (actionItems.size() != actionItemIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_CARRY_OVER_ACTION_ITEM);
        }

        for (ActionItem actionItem : actionItems) {
            boolean sameProject = actionItem.getProject().getId().equals(projectId);
            boolean incomplete = actionItem.getStatus() == ActionItemStatus.TODO
                    || actionItem.getStatus() == ActionItemStatus.IN_PROGRESS;
            if (!sameProject || !incomplete) {
                throw new BusinessException(ErrorCode.INVALID_CARRY_OVER_ACTION_ITEM);
            }
        }

        return actionItems;
    }

    private Project getProjectOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private Meeting getMeetingOrThrow(Long meetingId) {
        return meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
    }

    private void requireMember(Long projectId, Long userId) {
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
    }
}
