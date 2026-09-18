package com.followup.project.service;

import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.project.dto.ProjectMemberCreateReqDto;
import com.followup.project.dto.ProjectMemberResDto;
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
    public List<ProjectMemberResDto> getMembers(Long projectId) {
        getProjectOrThrow(projectId);
        requireMember(projectId, currentUserProvider.getCurrentUserId());

        return projectMemberRepository.findAllByProjectId(projectId).stream()
                .map(ProjectMemberResDto::from)
                .toList();
    }

    /**
     * OWNER만 가능하며, 이미 가입된 사용자를 email로 찾아 추가한다. 미가입 email은 404로 처리한다.
     * Project를 비관적 쓰기 락으로 먼저 잠가 같은 프로젝트에 대한 동시 addMember 요청 자체를
     * 직렬화한다 — 그래서 존재 여부 확인(existsByProjectIdAndUserId)과 insert를 별도 트랜잭션으로
     * 쪼개지 않고 하나의 트랜잭션 안에서 그대로 처리해도 레이스가 나지 않는다.
     */
    @Transactional
    public ProjectMemberResDto addMember(Long projectId, ProjectMemberCreateReqDto request) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
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

        return ProjectMemberResDto.from(member);
    }

    /**
     * OWNER만 가능하며, OWNER 역할은 제거할 수 없다(소유권 이전 기능이 아직 없음).
     * 제거되는 멤버가 담당자로 걸려 있던 ActionItem에서는 그 사람만 담당자 목록에서 뺀다.
     */
    @Transactional
    public void removeMember(Long projectId, Long userId) {
        getProjectOrThrow(projectId);
        requireOwner(projectId, currentUserProvider.getCurrentUserId());

        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_MEMBER_NOT_FOUND));

        if (member.getRole() == ProjectRole.OWNER) {
            throw new BusinessException(ErrorCode.PROJECT_OWNER_CANNOT_BE_REMOVED);
        }

        actionItemRepository.findAllByProjectIdAndAssigneesId(projectId, userId)
                .forEach(actionItem -> actionItem.removeAssignee(userId));

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
