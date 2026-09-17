package com.followup.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.followup.actionitem.dto.ActionItemCreateReqDto;
import com.followup.actionitem.dto.ActionItemDetailResDto;
import com.followup.actionitem.entity.Priority;
import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.actionitem.service.ActionItemService;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.notification.entity.Notification;
import com.followup.notification.repository.NotificationRepository;
import com.followup.project.dto.ProjectCreateReqDto;
import com.followup.project.dto.ProjectMemberCreateReqDto;
import com.followup.project.dto.ProjectResDto;
import com.followup.project.repository.ProjectRepository;
import com.followup.project.service.ProjectMemberService;
import com.followup.project.service.ProjectService;
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
class NotificationServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private ActionItemService actionItemService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ActionItemRepository actionItemRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    private Long ownerId;
    private Long memberId;
    private String memberEmail;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        ownerId = userRepository.save(User.builder()
                .email("owner-" + suffix + "@test.com").password("pw").name("Owner").build()).getId();
        memberEmail = "member-" + suffix + "@test.com";
        memberId = userRepository.save(User.builder()
                .email(memberEmail).password("pw").name("Member").build()).getId();
    }

    private void actingAs(Long userId) {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    private Long createProjectAsOwner() {
        actingAs(ownerId);
        ProjectResDto project = projectService.createProject(new ProjectCreateReqDto("Project", null));
        return project.id();
    }

    private ActionItemDetailResDto createActionItem(Long projectId) {
        return actionItemService.createActionItem(projectId,
                new ActionItemCreateReqDto("Task", null, null, null, Priority.MEDIUM));
    }

    private Notification createNotification(Long projectId, Long actionItemId, Long userId) {
        return notificationRepository.save(Notification.builder()
                .user(userRepository.getReferenceById(userId))
                .project(projectRepository.getReferenceById(projectId))
                .actionItem(actionItemRepository.getReferenceById(actionItemId))
                .taskTitle("Task")
                .remindAt(LocalDateTime.now().plusDays(1))
                .build());
    }

    @Test
    void deleteActionItem_cascadesNotification() {
        Long projectId = createProjectAsOwner();
        ActionItemDetailResDto item = createActionItem(projectId);
        createNotification(projectId, item.id(), ownerId);

        actionItemService.deleteActionItem(item.id());

        assertThat(notificationRepository.findByActionItemId(item.id())).isEmpty();
    }

    @Test
    void deleteAndMarkAsRead_deniedForNonOwner() {
        Long projectId = createProjectAsOwner();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));
        ActionItemDetailResDto item = createActionItem(projectId);
        Notification created = createNotification(projectId, item.id(), ownerId);

        actingAs(memberId);

        assertThatThrownBy(() -> notificationService.deleteNotification(created.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_ACCESS_DENIED);

        assertThatThrownBy(() -> notificationService.markAsRead(created.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_ACCESS_DENIED);
    }
}
