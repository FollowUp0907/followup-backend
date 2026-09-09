package com.followup.project.service;

import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.meeting.repository.MeetingRepository;
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
     * OWNER만 삭제할 수 있으며, Meeting/ActionItem이 남아 있으면 409로 막는다.
     * cascade 삭제를 두지 않았으므로 실제 업무 데이터를 먼저 정리해야 한다.
     */
    @Transactional
    public void deleteProject(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        requireOwner(projectId, currentUserProvider.getCurrentUserId());

        if (meetingRepository.existsByProjectId(projectId) || actionItemRepository.existsByProjectId(projectId)) {
            throw new BusinessException(ErrorCode.PROJECT_DELETE_CONFLICT);
        }

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
