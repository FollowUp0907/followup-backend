package com.followup.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.followup.actionitem.dto.ActionItemCreateReqDto;
import com.followup.actionitem.dto.ActionItemDetailResDto;
import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.Priority;
import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.actionitem.service.ActionItemService;
import com.followup.global.security.CurrentUserProvider;
import com.followup.meeting.dto.MeetingCreateReqDto;
import com.followup.meeting.dto.MeetingDetailResDto;
import com.followup.meeting.entity.Meeting;
import com.followup.meeting.repository.MeetingRepository;
import com.followup.meeting.service.MeetingService;
import com.followup.notification.repository.NotificationRepository;
import com.followup.project.dto.ProjectCreateReqDto;
import com.followup.project.dto.ProjectMemberCreateReqDto;
import com.followup.project.dto.ProjectResDto;
import com.followup.project.repository.ProjectMemberRepository;
import com.followup.project.repository.ProjectRepository;
import com.followup.project.service.ProjectMemberService;
import com.followup.project.service.ProjectService;
import com.followup.user.dto.MeResDto;
import com.followup.user.dto.UpdateMeReqDto;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private ActionItemService actionItemService;

    @Autowired
    private ActionItemRepository actionItemRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    private Long userId;
    private String userEmail;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        userEmail = "user-" + suffix + "@test.com";
        userId = userRepository.save(User.builder()
                .email(userEmail).password("pw").name("User").build()).getId();
    }

    private void actingAs(Long id) {
        when(currentUserProvider.getCurrentUserId()).thenReturn(id);
    }

    private Long createOtherUser(String label) {
        String suffix = UUID.randomUUID().toString();
        return userRepository.save(User.builder()
                .email(label + "-" + suffix + "@test.com").password("pw").name(label).build()).getId();
    }

    // ---- updateMe ----

    @Test
    void updateMe_changesNameAndReturnsIt() {
        actingAs(userId);

        MeResDto response = userService.updateMe(new UpdateMeReqDto("New Name"));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.email()).isEqualTo(userEmail);
        assertThat(userRepository.findById(userId).orElseThrow().getName()).isEqualTo("New Name");
    }

    // ---- deleteMe ----

    @Test
    void deleteMe_ownerProject_cascadeDeletesProjectAndSubData() {
        actingAs(userId);
        ProjectResDto project = projectService.createProject(new ProjectCreateReqDto("Owned", null));
        Long projectId = project.id();
        MeetingDetailResDto meeting = meetingService.createMeeting(projectId,
                new MeetingCreateReqDto("Sync", LocalDateTime.of(2026, 10, 1, 10, 0), "notes", null, null));
        ActionItemDetailResDto item = actionItemService.createActionItem(projectId,
                new ActionItemCreateReqDto("Task", null, java.util.List.of(userId), null, Priority.MEDIUM));

        userService.deleteMe();

        assertThat(projectRepository.findById(projectId)).isEmpty();
        assertThat(meetingRepository.findById(meeting.id())).isEmpty();
        assertThat(actionItemRepository.findById(item.id())).isEmpty();
        assertThat(projectMemberRepository.findAllByProjectId(projectId)).isEmpty();
        assertThat(notificationRepository.findAllByUserIdOrderByRemindAtDescIdDesc(userId)).isEmpty();
        assertThat(userRepository.findById(userId)).isEmpty();
    }

    @Test
    void deleteMe_memberOnlyProject_projectRemainsButUserLeavesAndIsUnassigned() {
        Long ownerId = createOtherUser("Owner");
        actingAs(ownerId);
        ProjectResDto project = projectService.createProject(new ProjectCreateReqDto("Shared", null));
        Long projectId = project.id();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(userEmail));

        actingAs(ownerId);
        ActionItemDetailResDto item = actionItemService.createActionItem(projectId,
                new ActionItemCreateReqDto("Task", null, java.util.List.of(userId), null, Priority.MEDIUM));

        actingAs(userId);
        userService.deleteMe();

        assertThat(projectRepository.findById(projectId)).isPresent();
        assertThat(projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)).isFalse();
        ActionItem reloaded = actionItemRepository.findById(item.id()).orElseThrow();
        assertThat(reloaded.getAssignees()).isEmpty();
        assertThat(userRepository.findById(userId)).isEmpty();
    }

    @Test
    void deleteMe_meetingCreatedInMemberOnlyProject_meetingRemainsWithNullCreator() {
        Long ownerId = createOtherUser("Owner");
        actingAs(ownerId);
        ProjectResDto project = projectService.createProject(new ProjectCreateReqDto("Shared", null));
        Long projectId = project.id();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(userEmail));

        actingAs(userId);
        MeetingDetailResDto meeting = meetingService.createMeeting(projectId,
                new MeetingCreateReqDto("Sync", LocalDateTime.of(2026, 10, 1, 10, 0), "notes", null, null));

        userService.deleteMe();

        Meeting reloaded = meetingRepository.findById(meeting.id()).orElseThrow();
        assertThat(reloaded.getCreatedBy()).isNull();
    }

    @Test
    void deleteMe_actionItemCreatedInMemberOnlyProject_itemRemainsWithNullCreator() {
        Long ownerId = createOtherUser("Owner");
        actingAs(ownerId);
        ProjectResDto project = projectService.createProject(new ProjectCreateReqDto("Shared", null));
        Long projectId = project.id();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(userEmail));

        actingAs(userId);
        ActionItemDetailResDto item = actionItemService.createActionItem(projectId,
                new ActionItemCreateReqDto("Task", null, null, null, Priority.MEDIUM));

        userService.deleteMe();

        ActionItem reloaded = actionItemRepository.findById(item.id()).orElseThrow();
        assertThat(reloaded.getCreatedBy()).isNull();
    }

    @Test
    void deleteMe_removesUserRow() {
        actingAs(userId);

        userService.deleteMe();

        assertThat(userRepository.findById(userId)).isEmpty();
    }
}
