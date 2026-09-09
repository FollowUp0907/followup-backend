package com.followup.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.project.dto.ProjectMemberCreateReqDto;
import com.followup.project.dto.ProjectMemberResDto;
import com.followup.project.dto.ProjectResDto;
import com.followup.project.entity.ProjectRole;
import com.followup.project.repository.ProjectMemberRepository;
import com.followup.project.repository.ProjectRepository;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProjectMemberServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ActionItemRepository actionItemRepository;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    private Long ownerId;
    private Long memberId;
    private Long outsiderId;
    private String memberEmail;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        ownerId = userRepository.save(User.builder()
                .email("owner-" + suffix + "@test.com")
                .password("pw")
                .name("Owner")
                .build()).getId();
        memberEmail = "member-" + suffix + "@test.com";
        memberId = userRepository.save(User.builder()
                .email(memberEmail)
                .password("pw")
                .name("Member")
                .build()).getId();
        outsiderId = userRepository.save(User.builder()
                .email("outsider-" + suffix + "@test.com")
                .password("pw")
                .name("Outsider")
                .build()).getId();
    }

    private void actingAs(Long userId) {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    private Long createProjectAsOwner() {
        actingAs(ownerId);
        ProjectResDto project = projectService.createProject(
                new com.followup.project.dto.ProjectCreateReqDto("Project", null));
        return project.id();
    }

    @Test
    void getMembers_success() {
        Long projectId = createProjectAsOwner();

        List<ProjectMemberResDto> members = projectMemberService.getMembers(projectId);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).role()).isEqualTo(ProjectRole.OWNER);
    }

    @Test
    void getMembers_nonMemberDenied() {
        Long projectId = createProjectAsOwner();

        actingAs(outsiderId);

        assertThatThrownBy(() -> projectMemberService.getMembers(projectId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);
    }

    @Test
    void addMember_ownerSuccess() {
        Long projectId = createProjectAsOwner();

        ProjectMemberResDto response = projectMemberService.addMember(
                projectId, new ProjectMemberCreateReqDto(memberEmail));

        assertThat(response.role()).isEqualTo(ProjectRole.MEMBER);
        assertThat(projectMemberRepository.existsByProjectIdAndUserId(projectId, memberId)).isTrue();
    }

    @Test
    void addMember_memberForbidden() {
        Long projectId = createProjectAsOwner();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));

        actingAs(memberId);

        assertThatThrownBy(() -> projectMemberService.addMember(
                projectId, new ProjectMemberCreateReqDto("outsider-" + UUID.randomUUID() + "@test.com")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_OWNER_REQUIRED);
    }

    @Test
    void addMember_userNotFound() {
        Long projectId = createProjectAsOwner();

        assertThatThrownBy(() -> projectMemberService.addMember(
                projectId, new ProjectMemberCreateReqDto("no-such-user@test.com")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void addMember_alreadyExists() {
        Long projectId = createProjectAsOwner();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));

        assertThatThrownBy(() -> projectMemberService.addMember(
                projectId, new ProjectMemberCreateReqDto(memberEmail)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
    }

    @Test
    void removeMember_ownerSuccess() {
        Long projectId = createProjectAsOwner();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));

        projectMemberService.removeMember(projectId, memberId);

        assertThat(projectMemberRepository.existsByProjectIdAndUserId(projectId, memberId)).isFalse();
    }

    @Test
    void removeMember_memberForbidden() {
        Long projectId = createProjectAsOwner();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));

        actingAs(memberId);

        assertThatThrownBy(() -> projectMemberService.removeMember(projectId, memberId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_OWNER_REQUIRED);
    }

    @Test
    void removeMember_ownerCannotBeRemoved() {
        Long projectId = createProjectAsOwner();

        assertThatThrownBy(() -> projectMemberService.removeMember(projectId, ownerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_OWNER_CANNOT_BE_REMOVED);
    }

    @Test
    void removeMember_notFound() {
        Long projectId = createProjectAsOwner();

        assertThatThrownBy(() -> projectMemberService.removeMember(projectId, outsiderId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_MEMBER_NOT_FOUND);
    }

    @Test
    void removeMember_unassignsActionItems() {
        Long projectId = createProjectAsOwner();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));

        ActionItem actionItem = actionItemRepository.save(ActionItem.builder()
                .project(projectRepository.getReferenceById(projectId))
                .assignee(userRepository.getReferenceById(memberId))
                .title("Task")
                .status(ActionItemStatus.TODO)
                .priority(Priority.MEDIUM)
                .build());

        projectMemberService.removeMember(projectId, memberId);

        ActionItem reloaded = actionItemRepository.findById(actionItem.getId()).orElseThrow();
        assertThat(reloaded.getAssignee()).isNull();
    }
}
