package com.followup.meeting.service;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.actionitem.entity.MeetingActionLink;
import com.followup.actionitem.repository.MeetingActionLinkRepository;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.meeting.dto.MeetingCreateReqDto;
import com.followup.meeting.dto.MeetingDetailResDto;
import com.followup.meeting.dto.MeetingListResDto;
import com.followup.meeting.dto.MeetingUpdateReqDto;
import com.followup.meeting.entity.Meeting;
import com.followup.meeting.entity.MeetingParticipant;
import com.followup.meeting.entity.MeetingStatus;
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
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public MeetingDetailResDto createMeeting(Long projectId, MeetingCreateReqDto request) {
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

        return MeetingDetailResDto.from(meeting, participants, carryOverItems);
    }

    @Transactional(readOnly = true)
    public List<MeetingListResDto> getMeetings(Long projectId) {
        getProjectOrThrow(projectId);
        requireMember(projectId, currentUserProvider.getCurrentUserId());

        return meetingRepository.findAllByProjectIdOrderByScheduledAtDesc(projectId).stream()
                .map(MeetingListResDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MeetingDetailResDto getMeeting(Long meetingId) {
        Meeting meeting = getMeetingOrThrow(meetingId);
        requireMember(meeting.getProject().getId(), currentUserProvider.getCurrentUserId());

        List<MeetingParticipant> participants = meetingParticipantRepository.findAllByMeetingId(meetingId);
        List<ActionItem> carryOverItems = meetingActionLinkRepository.findAllByMeetingId(meetingId).stream()
                .map(MeetingActionLink::getActionItem)
                .toList();

        return MeetingDetailResDto.from(meeting, participants, carryOverItems);
    }

    @Transactional
    public MeetingDetailResDto updateMeeting(Long meetingId, MeetingUpdateReqDto request) {
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

        return MeetingDetailResDto.from(meeting, participants, carryOverItems);
    }

    @Transactional
    public void deleteMeeting(Long meetingId) {
        Meeting meeting = getMeetingOrThrow(meetingId);
        requireMember(meeting.getProject().getId(), currentUserProvider.getCurrentUserId());

        meetingActionLinkRepository.deleteAllByMeetingId(meetingId);
        meetingParticipantRepository.deleteAllByMeetingId(meetingId);
        actionItemRepository.findAllByOriginMeetingId(meetingId)
                .forEach(ActionItem::detachOriginMeeting);

        meetingRepository.delete(meeting);
    }

    private List<Long> distinct(List<Long> ids) {
        return ids == null ? List.of() : ids.stream().distinct().toList();
    }

    private void validateParticipants(Long projectId, List<Long> participantIds) {
        for (Long userId : participantIds) {
            if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
                throw new BusinessException(ErrorCode.INVALID_MEETING_PARTICIPANT);
            }
        }
    }

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
