package com.followup.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.followup.actionitem.dto.ActionItemCreateReqDto;
import com.followup.actionitem.dto.ActionItemDetailResDto;
import com.followup.actionitem.entity.Priority;
import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.actionitem.service.ActionItemService;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.notification.dto.NotificationCreateReqDto;
import com.followup.notification.dto.NotificationResDto;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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

    @MockitoSpyBean
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

    @Test
    void createOrUpdateNotification_overwritesSingleNotificationPerActionItem() {
        Long projectId = createProjectAsOwner();
        ActionItemDetailResDto item = createActionItem(projectId);

        LocalDateTime first = LocalDateTime.now().plusDays(1);
        LocalDateTime second = LocalDateTime.now().plusDays(2);

        NotificationResDto firstResponse = notificationService.createOrUpdateNotification(
                item.id(), new NotificationCreateReqDto(first));
        NotificationResDto secondResponse = notificationService.createOrUpdateNotification(
                item.id(), new NotificationCreateReqDto(second));

        assertThat(secondResponse.id()).isEqualTo(firstResponse.id());
        assertThat(secondResponse.remindAt()).isEqualTo(second);
        assertThat(notificationRepository.findByActionItemId(item.id())).isPresent();
    }

    /**
     * findByActionItemId가 "없음"을 반환한 직후, 동시 요청이 먼저 알림을 커밋한 상황을 흉내낸다.
     * insert가 UNIQUE 제약 위반으로 실패해도 500이 아니라 정상 응답(기존 알림을 덮어쓴 결과)이 와야 한다.
     */
    @Test
    void createOrUpdateNotification_fallsBackToUpdateOnUniqueConstraintRace() {
        Long projectId = createProjectAsOwner();
        ActionItemDetailResDto item = createActionItem(projectId);
        LocalDateTime racedRemindAt = LocalDateTime.now().plusHours(1);
        LocalDateTime requestedRemindAt = LocalDateTime.now().plusDays(1);

        Notification racedNotification = Notification.builder()
                .user(userRepository.getReferenceById(ownerId))
                .project(projectRepository.getReferenceById(projectId))
                .actionItem(actionItemRepository.getReferenceById(item.id()))
                .taskTitle("Task")
                .remindAt(racedRemindAt)
                .build();

        // 1번째 호출(서비스의 최초 조회): "없음"을 돌려주면서, 동시 요청이 먼저 커밋한 것처럼 알림을 심어둔다.
        // 2번째 호출(유니크 제약 위반 이후 폴백 조회): 방금 심어둔 알림을 찾은 것처럼 돌려준다.
        doAnswer(invocation -> {
            notificationRepository.saveAndFlush(racedNotification);
            return Optional.<Notification>empty();
        }).doAnswer(invocation -> Optional.of(racedNotification))
                .when(notificationRepository).findByActionItemId(eq(item.id()));

        NotificationResDto response = notificationService.createOrUpdateNotification(
                item.id(), new NotificationCreateReqDto(requestedRemindAt));

        assertThat(response.remindAt()).isEqualTo(requestedRemindAt);
        assertThat(notificationRepository.findByActionItemId(item.id()))
                .hasValueSatisfying(n -> assertThat(n.getRemindAt()).isEqualTo(requestedRemindAt));
    }

    @Test
    void deleteActionItem_cascadesNotification() {
        Long projectId = createProjectAsOwner();
        ActionItemDetailResDto item = createActionItem(projectId);
        notificationService.createOrUpdateNotification(item.id(),
                new NotificationCreateReqDto(LocalDateTime.now().plusDays(1)));

        actionItemService.deleteActionItem(item.id());

        assertThat(notificationRepository.findByActionItemId(item.id())).isEmpty();
    }

    @Test
    void deleteAndMarkAsRead_deniedForNonOwner() {
        Long projectId = createProjectAsOwner();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));
        ActionItemDetailResDto item = createActionItem(projectId);
        NotificationResDto created = notificationService.createOrUpdateNotification(
                item.id(), new NotificationCreateReqDto(LocalDateTime.now().plusDays(1)));

        actingAs(memberId);

        assertThatThrownBy(() -> notificationService.deleteNotification(created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_ACCESS_DENIED);

        assertThatThrownBy(() -> notificationService.markAsRead(created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_ACCESS_DENIED);
    }
}
