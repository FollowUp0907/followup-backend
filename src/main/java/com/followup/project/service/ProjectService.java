package com.followup.project.service;

import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.meeting.repository.MeetingRepository;
import com.followup.project.dto.ProjectCreateRequest;
import com.followup.project.dto.ProjectResponse;
import com.followup.project.dto.ProjectUpdateRequest;
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

    @Transactional
    public ProjectResponse createProject(ProjectCreateRequest request) {
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

        return ProjectResponse.from(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getProjects() {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        return projectMemberRepository.findAllByUserId(currentUserId).stream()
                .map(ProjectMember::getProject)
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        requireMember(projectId, currentUserProvider.getCurrentUserId());
        return ProjectResponse.from(project);
    }

    @Transactional
    public ProjectResponse updateProject(Long projectId, ProjectUpdateRequest request) {
        Project project = getProjectOrThrow(projectId);
        requireOwner(projectId, currentUserProvider.getCurrentUserId());

        if (request.name() != null && request.name().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        project.update(request.name(), request.description());
        return ProjectResponse.from(project);
    }

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
