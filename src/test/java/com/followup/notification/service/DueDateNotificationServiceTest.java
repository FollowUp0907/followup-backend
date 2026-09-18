package com.followup.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.followup.actionitem.dto.ActionItemCreateReqDto;
import com.followup.actionitem.dto.ActionItemDetailResDto;
import com.followup.actionitem.entity.Priority;
import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.actionitem.service.ActionItemService;
import com.followup.global.security.CurrentUserProvider;
import com.followup.notification.dto.NotificationResDto;
import com.followup.notification.entity.Notification;
import com.followup.notification.entity.NotificationType;
import com.followup.notification.repository.NotificationReadRepository;
import com.followup.notification.repository.NotificationRepository;
import com.followup.project.dto.ProjectCreateReqDto;
import com.followup.project.repository.ProjectRepository;
import com.followup.project.service.ProjectService;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private NotificationRepository notificationRepository;

    @Autowired
    private ActionItemRepository actionItemRepository;

    @Autowired
    private ProjectRepository projectRepository;

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
                new ActionItemCreateReqDto("Task", null, List.of(userId), dueDate, Priority.MEDIUM));
    }

    /** 마감일이 없는 업무 — OVERDUE/DUE_SOON 가상 계산 대상이 아니라, 저장된 알림만 붙이기 위한 용도다. */
    private ActionItemDetailResDto createPlainTask() {
        return actionItemService.createActionItem(projectId,
                new ActionItemCreateReqDto("Plain task", null, null, null, Priority.MEDIUM));
    }

    private Notification saveStoredNotification(Long actionItemId) {
        return notificationRepository.save(Notification.builder()
                .user(userRepository.getReferenceById(userId))
                .project(projectRepository.getReferenceById(projectId))
                .actionItem(actionItemRepository.getReferenceById(actionItemId))
                .taskTitle("Plain task")
                .remindAt(LocalDateTime.now())
                .type(NotificationType.TASK_CREATED)
                .build());
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

    /**
     * getNotifications()의 readAt==null 개수와 getUnreadCount()가 항상 일치해야 한다는 안전장치다 —
     * 지연/임박(읽음·안읽음)/저장(읽음·안읽음) 알림을 전부 섞어서, 목록 기준 계산과 COUNT 쿼리 기준
     * 계산이 어긋나지 않는지 확인한다.
     */
    @Test
    void unreadCount_matchesListUnreadCount_withOverdueDueSoonAndStoredNotificationsMixed() {
        LocalDate today = LocalDate.now();

        // 지연 2건
        createTaskDue(today.minusDays(1));
        createTaskDue(today.minusDays(5));

        // 마감임박 1건(오늘 안 읽음)
        createTaskDue(today.plusDays(2));

        // 마감임박 1건(오늘 이미 읽음)
        ActionItemDetailResDto dueSoonRead = createTaskDue(today.plusDays(4));
        Long dueSoonReadVirtualId = findFor(notificationService.getNotifications(false), dueSoonRead.id()).id();
        notificationService.markAsRead(dueSoonReadVirtualId);

        // 저장된 알림 미읽음 2건 + 읽음 1건
        saveStoredNotification(createPlainTask().id());
        saveStoredNotification(createPlainTask().id());
        Notification readStored = saveStoredNotification(createPlainTask().id());
        readStored.markAsRead();
        notificationRepository.save(readStored);

        List<NotificationResDto> list = notificationService.getNotifications(false);
        long expectedUnread = list.stream().filter(n -> n.readAt() == null).count();

        long actualUnread = notificationService.getUnreadCount();

        assertThat(actualUnread).isEqualTo(expectedUnread);
        // 지연 2 + 임박(안읽음) 1 + 저장(안읽음) 2 = 5. 임박(읽음) 1건, 저장(읽음) 1건은 제외된다.
        assertThat(actualUnread).isEqualTo(5);
    }

    /** assignees 목록에 담당자로 지정된 지연 업무는 OVERDUE로 잡히고 unread-count에도 반영돼야 한다. */
    @Test
    void overdueTask_assignedViaAssigneesList_isCountedAsOverdueAndInUnreadCount() {
        LocalDate today = LocalDate.now();
        ActionItemDetailResDto item = actionItemService.createActionItem(projectId,
                new ActionItemCreateReqDto("Assigned overdue", null, List.of(userId), today.minusDays(2),
                        Priority.MEDIUM));

        List<NotificationResDto> notifications = notificationService.getNotifications(false);
        assertThat(findFor(notifications, item.id()).type()).isEqualTo(NotificationType.OVERDUE);

        long expectedUnread = notifications.stream().filter(n -> n.readAt() == null).count();
        assertThat(notificationService.getUnreadCount()).isEqualTo(expectedUnread);
    }
}
