package com.followup.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.followup.actionitem.dto.ActionItemCreateReqDto;
import com.followup.actionitem.dto.ActionItemDetailResDto;
import com.followup.actionitem.entity.Priority;
import com.followup.actionitem.service.ActionItemService;
import com.followup.global.security.CurrentUserProvider;
import com.followup.notification.dto.NotificationResDto;
import com.followup.notification.entity.NotificationType;
import com.followup.notification.repository.NotificationReadRepository;
import com.followup.project.dto.ProjectCreateReqDto;
import com.followup.project.service.ProjectService;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** OVERDUE/DUE_SOON 가상 알림 계산 + unread-count를 검증한다. */
@SpringBootTest
@Transactional
class DueDateNotificationServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ActionItemService actionItemService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationReadRepository notificationReadRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    private Long userId;
    private Long projectId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        userId = userRepository.save(User.builder()
                .email("due-" + suffix + "@test.com").password("pw").name("Due Tester").build()).getId();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        projectId = projectService.createProject(new ProjectCreateReqDto("Due Project", null)).id();
    }

    /** 본인을 담당자로 지정해 만든다 — 담당 업무 조회 대상이면서 TaskAssignedEvent 발행 대상이 아니다. */
    private ActionItemDetailResDto createTaskDue(LocalDate dueDate) {
        return actionItemService.createActionItem(projectId,
                new ActionItemCreateReqDto("Task", null, userId, dueDate, Priority.MEDIUM));
    }

    private NotificationResDto findFor(List<NotificationResDto> notifications, Long actionItemId) {
        return notifications.stream()
                .filter(n -> actionItemId.equals(n.actionItemId()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void classifiesOverdueDueSoonAndOutOfRangeCorrectly() {
        LocalDate today = LocalDate.now();
        ActionItemDetailResDto overdue = createTaskDue(today.minusDays(1));
        ActionItemDetailResDto dueToday = createTaskDue(today);
        ActionItemDetailResDto dueSoon = createTaskDue(today.plusDays(3));
        ActionItemDetailResDto notDue = createTaskDue(today.plusDays(10));

        List<NotificationResDto> notifications = notificationService.getNotifications(false);

        assertThat(findFor(notifications, overdue.id()).type()).isEqualTo(NotificationType.OVERDUE);
        assertThat(findFor(notifications, dueToday.id()).type()).isEqualTo(NotificationType.DUE_SOON);
        assertThat(findFor(notifications, dueSoon.id()).type()).isEqualTo(NotificationType.DUE_SOON);
        assertThat(notifications).noneMatch(n -> notDue.id().equals(n.actionItemId()));
    }

    @Test
    void virtualNotificationId_isNegativeActionItemId() {
        ActionItemDetailResDto item = createTaskDue(LocalDate.now().minusDays(1));

        NotificationResDto notification = findFor(notificationService.getNotifications(false), item.id());

        assertThat(notification.id()).isEqualTo(-item.id());
    }

    @Test
    void dueSoon_markAsRead_setsReadAtForTodayOnly_notTomorrow() {
        LocalDate today = LocalDate.now();
        ActionItemDetailResDto item = createTaskDue(today.plusDays(2));

        Long virtualId = findFor(notificationService.getNotifications(false), item.id()).id();

        NotificationResDto marked = notificationService.markAsRead(virtualId);
        assertThat(marked.readAt()).isNotNull();

        NotificationResDto reQueried = findFor(notificationService.getNotifications(false), item.id());
        assertThat(reQueried.readAt()).isNotNull();

        String dedupKey = "duesoon-" + item.id();
        assertThat(notificationReadRepository.existsByIdUserIdAndIdDedupKeyAndIdReadDate(userId, dedupKey, today))
                .isTrue();
        assertThat(notificationReadRepository.existsByIdUserIdAndIdDedupKeyAndIdReadDate(
                        userId, dedupKey, today.plusDays(1)))
                .isFalse();
    }

    @Test
    void overdue_markAsRead_staysUnreadAlways() {
        ActionItemDetailResDto item = createTaskDue(LocalDate.now().minusDays(3));

        Long virtualId = findFor(notificationService.getNotifications(false), item.id()).id();

        NotificationResDto marked = notificationService.markAsRead(virtualId);
        assertThat(marked.readAt()).isNull();

        NotificationResDto reQueried = findFor(notificationService.getNotifications(false), item.id());
        assertThat(reQueried.readAt()).isNull();
    }

    @Test
    void unreadCount_matchesUnreadCountOfMergedList() {
        LocalDate today = LocalDate.now();
        createTaskDue(today.minusDays(1));
        ActionItemDetailResDto dueSoonItem = createTaskDue(today.plusDays(1));

        long before = notificationService.getUnreadCount();
        long expectedBefore = notificationService.getNotifications(false).stream()
                .filter(n -> n.readAt() == null)
                .count();
        assertThat(before).isEqualTo(expectedBefore);

        Long virtualId = findFor(notificationService.getNotifications(false), dueSoonItem.id()).id();
        notificationService.markAsRead(virtualId);

        long after = notificationService.getUnreadCount();
        long expectedAfter = notificationService.getNotifications(false).stream()
                .filter(n -> n.readAt() == null)
                .count();
        assertThat(after).isEqualTo(expectedAfter);
        assertThat(after).isEqualTo(before - 1);
    }
}
