package com.followup.project.service;

import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.project.dto.ProjectMemberCreateRequest;
import com.followup.project.dto.ProjectMemberResponse;
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
public class ProjectMemberService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final ActionItemRepository actionItemRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> getMembers(Long projectId) {
        getProjectOrThrow(projectId);
        requireMember(projectId, currentUserProvider.getCurrentUserId());

        return projectMemberRepository.findAllByProjectId(projectId).stream()
                .map(ProjectMemberResponse::from)
                .toList();
    }

    @Transactional
    public ProjectMemberResponse addMember(Long projectId, ProjectMemberCreateRequest request) {
        Project project = getProjectOrThrow(projectId);
        requireOwner(projectId, currentUserProvider.getCurrentUserId());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, user.getId())) {
            throw new BusinessException(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
        }

        ProjectMember member = ProjectMember.builder()
                .project(project)
                .user(user)
                .role(ProjectRole.MEMBER)
                .build();
        projectMemberRepository.save(member);

        return ProjectMemberResponse.from(member);
    }

    @Transactional
    public void removeMember(Long projectId, Long userId) {
        getProjectOrThrow(projectId);
        requireOwner(projectId, currentUserProvider.getCurrentUserId());

        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_MEMBER_NOT_FOUND));

        if (member.getRole() == ProjectRole.OWNER) {
            throw new BusinessException(ErrorCode.PROJECT_OWNER_CANNOT_BE_REMOVED);
        }

        actionItemRepository.findAllByProjectIdAndAssigneeId(projectId, userId)
                .forEach(actionItem -> actionItem.unassign());

        projectMemberRepository.delete(member);
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
