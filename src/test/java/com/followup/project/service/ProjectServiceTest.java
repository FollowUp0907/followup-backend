package com.followup.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.project.dto.ProjectCreateReqDto;
import com.followup.project.dto.ProjectResDto;
import com.followup.project.dto.ProjectUpdateReqDto;
import com.followup.project.entity.ProjectMember;
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
class ProjectServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    private Long ownerId;
    private Long memberId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        ownerId = userRepository.save(User.builder()
                .email("owner-" + suffix + "@test.com")
                .password("pw")
                .name("Owner")
                .build()).getId();
        memberId = userRepository.save(User.builder()
                .email("member-" + suffix + "@test.com")
                .password("pw")
                .name("Member")
                .build()).getId();
    }

    private void actingAs(Long userId) {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    private void addMember(Long projectId, Long userId, ProjectRole role) {
        projectMemberRepository.save(ProjectMember.builder()
                .project(projectRepository.getReferenceById(projectId))
                .user(userRepository.getReferenceById(userId))
                .role(role)
                .build());
    }

    @Test
    void createProject_success() {
        actingAs(ownerId);

        ProjectResDto response = projectService.createProject(new ProjectCreateReqDto("Project A", "desc"));

        assertThat(response.name()).isEqualTo("Project A");
        assertThat(response.createdBy()).isEqualTo(ownerId);
    }

    @Test
    void createProject_registersOwnerMember() {
        actingAs(ownerId);

        ProjectResDto response = projectService.createProject(new ProjectCreateReqDto("Project A", null));

        assertThat(projectMemberRepository.existsByProjectIdAndUserId(response.id(), ownerId)).isTrue();
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(response.id(), ownerId).orElseThrow();
        assertThat(member.getRole()).isEqualTo(ProjectRole.OWNER);
    }

    @Test
    void getProjects_returnsOnlyMyProjects() {
        actingAs(ownerId);
        projectService.createProject(new ProjectCreateReqDto("Owner Project", null));

        actingAs(memberId);
        List<ProjectResDto> myProjects = projectService.getProjects();

        assertThat(myProjects).isEmpty();
    }

    @Test
    void getProject_success() {
        actingAs(ownerId);
        ProjectResDto created = projectService.createProject(new ProjectCreateReqDto("P", null));

        ProjectResDto fetched = projectService.getProject(created.id());

        assertThat(fetched.id()).isEqualTo(created.id());
    }

    @Test
    void getProject_nonMemberDenied() {
        actingAs(ownerId);
        ProjectResDto created = projectService.createProject(new ProjectCreateReqDto("P", null));

        actingAs(memberId);

        assertThatThrownBy(() -> projectService.getProject(created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);
    }

    @Test
    void getProject_notFound() {
        actingAs(ownerId);

        assertThatThrownBy(() -> projectService.getProject(9_999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
    }

    @Test
    void updateProject_ownerSuccess() {
        actingAs(ownerId);
        ProjectResDto created = projectService.createProject(new ProjectCreateReqDto("P", null));

        ProjectResDto updated = projectService.updateProject(created.id(), new ProjectUpdateReqDto("New name", null));

        assertThat(updated.name()).isEqualTo("New name");
    }

    @Test
    void updateProject_memberDenied() {
        actingAs(ownerId);
        ProjectResDto created = projectService.createProject(new ProjectCreateReqDto("P", null));
        addMember(created.id(), memberId, ProjectRole.MEMBER);

        actingAs(memberId);

        assertThatThrownBy(() -> projectService.updateProject(created.id(), new ProjectUpdateReqDto("x", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_OWNER_REQUIRED);
    }

    @Test
    void deleteProject_ownerSuccess() {
        actingAs(ownerId);
        ProjectResDto created = projectService.createProject(new ProjectCreateReqDto("P", null));

        projectService.deleteProject(created.id());

        assertThat(projectRepository.findById(created.id())).isEmpty();
    }

    @Test
    void deleteProject_memberDenied() {
        actingAs(ownerId);
        ProjectResDto created = projectService.createProject(new ProjectCreateReqDto("P", null));
        addMember(created.id(), memberId, ProjectRole.MEMBER);

        actingAs(memberId);

        assertThatThrownBy(() -> projectService.deleteProject(created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_OWNER_REQUIRED);
    }
}
