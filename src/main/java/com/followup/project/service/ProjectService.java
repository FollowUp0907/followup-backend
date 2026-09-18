package com.followup.project.service;

import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.actionitem.repository.MeetingActionLinkRepository;
import com.followup.ai.repository.AiAnalysisRunRepository;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.meeting.repository.DecisionRepository;
import com.followup.meeting.repository.MeetingParticipantRepository;
import com.followup.meeting.repository.MeetingRepository;
import com.followup.notification.repository.NotificationRepository;
import com.followup.project.dto.ProjectCreateReqDto;
import com.followup.project.dto.ProjectResDto;
import com.followup.project.dto.ProjectUpdateReqDto;
import com.followup.project.entity.Project;
import com.followup.project.entity.ProjectMember;
import com.followup.project.entity.ProjectRole;
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
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final MeetingRepository meetingRepository;
    private final ActionItemRepository actionItemRepository;
    private final DecisionRepository decisionRepository;
    private final AiAnalysisRunRepository aiAnalysisRunRepository;
    private final MeetingActionLinkRepository meetingActionLinkRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final NotificationRepository notificationRepository;
    private final CurrentUserProvider currentUserProvider;

    /**
     * 생성자를 OWNER {@link ProjectMember}로 함께 등록한다.
     * 접근 권한이 멤버십 기준으로 검사되므로, OWNER가 없으면 생성자도 접근할 수 없다.
     */
    @Transactional
    public ProjectResDto createProject(ProjectCreateReqDto request) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Project project = Project.builder()
                .name(request.name())
                .description(request.description())
                .createdBy(creator)
                .build();
        projectRepository.save(project);

        if (!projectMemberRepository.existsByProjectIdAndUserId(project.getId(), currentUserId)) {
            ProjectMember owner = ProjectMember.builder()
                    .project(project)
                    .user(creator)
                    .role(ProjectRole.OWNER)
                    .build();
            projectMemberRepository.save(owner);
        }

        return ProjectResDto.from(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResDto> getProjects() {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        return projectMemberRepository.findAllByUserId(currentUserId).stream()
                .map(ProjectMember::getProject)
                .map(ProjectResDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResDto getProject(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        requireMember(projectId, currentUserProvider.getCurrentUserId());
        return ProjectResDto.from(project);
    }

    /** OWNER만 프로젝트 정보를 수정할 수 있다. */
    @Transactional
    public ProjectResDto updateProject(Long projectId, ProjectUpdateReqDto request) {
        Project project = getProjectOrThrow(projectId);
        requireOwner(projectId, currentUserProvider.getCurrentUserId());

        if (request.name() != null && request.name().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        project.update(request.name(), request.description());
        return ProjectResDto.from(project);
    }

    /**
     * OWNER만 삭제할 수 있다. Meeting은 soft-delete 대상이라 findIdsByProjectId로 삭제 여부와 무관하게
     * 전체 id를 구한 뒤, 하위 데이터를 애플리케이션 레벨에서 명시적 순서로 정리하고 마지막에 하드 삭제한다.
     * meeting_action_links는 action_items.id를 참조하므로(FK RESTRICT) action_items보다 먼저 지운다.
     */
    @Transactional
    public void deleteProject(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        requireOwner(projectId, currentUserProvider.getCurrentUserId());

        List<Long> meetingIds = meetingRepository.findIdsByProjectId(projectId);
        List<Long> actionItemIds = actionItemRepository.findIdsByProjectId(projectId);

        meetingActionLinkRepository.deleteAllByMeetingIdIn(meetingIds);
        meetingParticipantRepository.deleteAllByMeetingIdIn(meetingIds);
        notificationRepository.deleteAllByActionItemIdIn(actionItemIds);
        if (!actionItemIds.isEmpty()) {
            actionItemRepository.deleteAllAssigneesByActionItemIdIn(actionItemIds);
        }
        actionItemRepository.deleteAllByProjectId(projectId);
        decisionRepository.deleteAllByMeetingIdIn(meetingIds);
        aiAnalysisRunRepository.deleteAllByMeetingIdIn(meetingIds);
        meetingRepository.deleteAllById(meetingIds);

        projectMemberRepository.deleteAllByProjectId(projectId);
        projectRepository.delete(project);
    }

    private Project getProjectOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private ProjectMember requireMember(Long projectId, Long userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED));
    }

    private void requireOwner(Long projectId, Long userId) {
        ProjectMember member = requireMember(projectId, userId);
        if (member.getRole() != ProjectRole.OWNER) {
            throw new BusinessException(ErrorCode.PROJECT_OWNER_REQUIRED);
        }
    }
}
