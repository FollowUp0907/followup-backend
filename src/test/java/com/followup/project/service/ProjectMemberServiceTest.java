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
import java.util.Set;
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
    private String outsiderEmail;

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
        outsiderEmail = "outsider-" + suffix + "@test.com";
        outsiderId = userRepository.save(User.builder()
                .email(outsiderEmail)
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

    /** 본인이 아닌 다른 멤버를 제거하려는 시도는 여전히 OWNER만 가능하다. */
    @Test
    void removeMember_memberForbiddenToRemoveSomeoneElse() {
        Long projectId = createProjectAsOwner();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(outsiderEmail));

        actingAs(memberId);

        assertThatThrownBy(() -> projectMemberService.removeMember(projectId, outsiderId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_OWNER_REQUIRED);
    }

    /** 본인이 본인을 제거하는 "나가기"는 OWNER가 아니어도 허용되고, 담당 업무는 담당자만 빠진 채 남는다. */
    @Test
    void removeMember_selfRemoval_leavesProjectAndUnassignsActionItems() {
        Long projectId = createProjectAsOwner();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));

        ActionItem actionItem = actionItemRepository.save(ActionItem.builder()
                .project(projectRepository.getReferenceById(projectId))
                .title("Task")
                .status(ActionItemStatus.TODO)
                .priority(Priority.MEDIUM)
                .build());
        actionItem.replaceAssignees(Set.of(userRepository.getReferenceById(memberId)));
        actionItemRepository.save(actionItem);

        actingAs(memberId);
        projectMemberService.removeMember(projectId, memberId);

        assertThat(projectMemberRepository.existsByProjectIdAndUserId(projectId, memberId)).isFalse();
        ActionItem reloaded = actionItemRepository.findById(actionItem.getId()).orElseThrow();
        assertThat(reloaded.getAssignees()).isEmpty();
    }

    /** OWNER가 본인 스스로 나가려는 시도도(자기 자신을 대상으로 호출) 여전히 막혀야 한다. */
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
                .title("Task")
                .status(ActionItemStatus.TODO)
                .priority(Priority.MEDIUM)
                .build());
        actionItem.replaceAssignees(Set.of(userRepository.getReferenceById(memberId)));
        actionItemRepository.save(actionItem);

        projectMemberService.removeMember(projectId, memberId);

        ActionItem reloaded = actionItemRepository.findById(actionItem.getId()).orElseThrow();
        assertThat(reloaded.getAssignees()).isEmpty();
    }

    @Test
    void removeMember_revokesProjectAccessImmediately() {
        Long projectId = createProjectAsOwner();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));

        projectMemberService.removeMember(projectId, memberId);

        actingAs(memberId);

        assertThatThrownBy(() -> projectService.getProject(projectId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);
    }
}
