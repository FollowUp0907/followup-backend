package com.followup.notification.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.followup.actionitem.dto.ActionItemCreateReqDto;
import com.followup.actionitem.dto.ActionItemDetailResDto;
import com.followup.actionitem.dto.ActionItemUpdateReqDto;
import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.actionitem.service.ActionItemService;
import com.followup.global.security.CurrentUserProvider;
import com.followup.meeting.dto.MeetingCreateReqDto;
import com.followup.meeting.dto.MeetingDetailResDto;
import com.followup.meeting.repository.MeetingParticipantRepository;
import com.followup.meeting.repository.MeetingRepository;
import com.followup.meeting.service.MeetingService;
import com.followup.notification.entity.Notification;
import com.followup.notification.entity.NotificationType;
import com.followup.notification.repository.NotificationRepository;
import com.followup.project.dto.ProjectCreateReqDto;
import com.followup.project.dto.ProjectMemberCreateReqDto;
import com.followup.project.dto.ProjectResDto;
import com.followup.project.repository.ProjectMemberRepository;
import com.followup.project.repository.ProjectRepository;
import com.followup.project.service.ProjectMemberService;
import com.followup.project.service.ProjectService;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TaskAssigned/TaskUpdated/TaskCompleted 이벤트 → 알림 변환을 실제 커밋을 거쳐 검증한다.
 * @TransactionalEventListener(AFTER_COMMIT)는 실제 커밋이 있어야 동작하므로, 이 클래스는 일부러
 * class-level @Transactional을 쓰지 않는다 — 그걸 쓰면 서비스 호출이 테스트의 롤백 트랜잭션 안에
 * 갇혀 커밋 자체가 없고, 리스너가 아예 호출되지 않는다. 대신 concurrency 패키지 테스트들과 같은
 * 스타일로 @BeforeEach/@AfterEach에서 직접 생성/정리한다.
 */
@SpringBootTest
class ActionItemNotificationListenerTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private ActionItemService actionItemService;

    @Autowired
    private ActionItemRepository actionItemRepository;

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private MeetingParticipantRepository meetingParticipantRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    /** A: 프로젝트 오너 겸 기본 생성자, B/C: 멤버. */
    private Long ownerId;
    private Long memberBId;
    private Long memberCId;
    private Long projectId;

    private final List<Long> actionItemIds = new ArrayList<>();
    private Long meetingId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        ownerId = userRepository.save(User.builder()
                .email("a-" + suffix + "@test.com").password("pw").name("A").build()).getId();
        Long b = userRepository.save(User.builder()
                .email("b-" + suffix + "@test.com").password("pw").name("B").build()).getId();
        Long c = userRepository.save(User.builder()
                .email("c-" + suffix + "@test.com").password("pw").name("C").build()).getId();
        memberBId = b;
        memberCId = c;

        actingAs(ownerId);
        ProjectResDto project = projectService.createProject(new ProjectCreateReqDto("Notif Project", null));
        projectId = project.id();
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(emailOf(b)));
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(emailOf(c)));

        actionItemIds.clear();
        meetingId = null;
    }

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Long actionItemId : actionItemIds) {
                notificationRepository.deleteByActionItemId(actionItemId);
            }
            if (!actionItemIds.isEmpty()) {
                actionItemRepository.deleteAllById(actionItemIds);
            }
            if (meetingId != null) {
                meetingParticipantRepository.deleteAllByMeetingId(meetingId);
                meetingRepository.deleteById(meetingId);
            }
            projectMemberRepository.deleteAllByProjectId(projectId);
            projectRepository.deleteById(projectId);
            userRepository.deleteById(memberCId);
            userRepository.deleteById(memberBId);
            userRepository.deleteById(ownerId);
        });
    }

    private void actingAs(Long userId) {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    private String emailOf(Long userId) {
        return userRepository.findById(userId).orElseThrow().getEmail();
    }

    private List<Notification> notificationsFor(Long userId) {
        return notificationRepository.findAllByUserIdOrderByRemindAtDescIdDesc(userId);
    }

    private long countByType(Long userId, NotificationType type) {
        return notificationsFor(userId).stream().filter(n -> n.getType() == type).count();
    }

    private ActionItemDetailResDto createTask(Long assigneeId) {
        ActionItemDetailResDto item = actionItemService.createActionItem(projectId,
                new ActionItemCreateReqDto("Task", null, assigneeId, null, Priority.MEDIUM));
        actionItemIds.add(item.id());
        return item;
    }

    private Long createMeetingWithParticipants(List<Long> participantIds) {
        actingAs(ownerId);
        MeetingDetailResDto meeting = meetingService.createMeeting(projectId,
                new MeetingCreateReqDto("Sync", LocalDateTime.of(2026, 9, 20, 10, 0), "notes", participantIds, null));
        meetingId = meeting.id();
        return meetingId;
    }

    private ActionItem createTaskWithOriginMeeting(Long originMeetingId, Long assigneeId, Long creatorId) {
        ActionItem item = actionItemRepository.save(ActionItem.builder()
                .project(projectRepository.getReferenceById(projectId))
                .originMeeting(meetingRepository.getReferenceById(originMeetingId))
                .assignee(assigneeId != null ? userRepository.getReferenceById(assigneeId) : null)
                .createdBy(creatorId != null ? userRepository.getReferenceById(creatorId) : null)
                .title("From meeting")
                .status(ActionItemStatus.TODO)
                .priority(Priority.MEDIUM)
                .build());
        actionItemIds.add(item.getId());
        return item;
    }

    // ---- TASK_CREATED ----

    @Test
    void taskCreated_assigneeOtherThanActor_getsOneNotification() {
        actingAs(ownerId);
        createTask(memberBId);

        assertThat(countByType(memberBId, NotificationType.TASK_CREATED)).isEqualTo(1);
        assertThat(notificationsFor(ownerId)).isEmpty();
    }

    @Test
    void taskCreated_selfAssigned_noNotification() {
        actingAs(ownerId);
        createTask(ownerId);

        assertThat(notificationsFor(ownerId)).isEmpty();
    }

    @Test
    void taskCreated_reassignedToAnother_newAssigneeGetsOneNotification() {
        actingAs(ownerId);
        ActionItemDetailResDto item = createTask(memberBId);

        actionItemService.updateActionItem(item.id(),
                new ActionItemUpdateReqDto(null, null, memberCId, null, null, null));

        assertThat(countByType(memberCId, NotificationType.TASK_CREATED)).isEqualTo(1);
    }

    // ---- TASK_UPDATED ----

    @Test
    void taskUpdated_titleChangedByOther_assigneeGetsNotification() {
        actingAs(ownerId);
        ActionItemDetailResDto item = createTask(memberBId);

        actionItemService.updateActionItem(item.id(),
                new ActionItemUpdateReqDto("New title", null, null, null, null, null));

        assertThat(countByType(memberBId, NotificationType.TASK_UPDATED)).isEqualTo(1);
    }

    @Test
    void taskUpdated_titleChangedBySelf_noNotification() {
        actingAs(ownerId);
        ActionItemDetailResDto item = createTask(ownerId);

        actionItemService.updateActionItem(item.id(),
                new ActionItemUpdateReqDto("New title", null, null, null, null, null));

        assertThat(notificationsFor(ownerId)).isEmpty();
    }

    @Test
    void statusOnlyChange_noTaskUpdatedNotification() {
        actingAs(ownerId);
        ActionItemDetailResDto item = createTask(memberBId);

        actionItemService.updateActionItem(item.id(),
                new ActionItemUpdateReqDto(null, null, null, null, ActionItemStatus.IN_PROGRESS, null));

        assertThat(countByType(memberBId, NotificationType.TASK_UPDATED)).isZero();
    }

    // ---- TASK_COMPLETED ----

    @Test
    void taskCompleted_notifiesAssigneeCreatorAndMeetingParticipantsOnce() {
        Long meeting = createMeetingWithParticipants(List.of(memberBId, memberCId));
        ActionItem item = createTaskWithOriginMeeting(meeting, memberBId, ownerId);

        actingAs(memberBId);
        actionItemService.updateActionItem(item.getId(),
                new ActionItemUpdateReqDto(null, null, null, null, ActionItemStatus.DONE, null));

        // B는 담당자이면서 회의 참여자이기도 하지만, 중복 제거로 1건만 받는다.
        assertThat(countByType(memberBId, NotificationType.TASK_COMPLETED)).isEqualTo(1);
        assertThat(countByType(ownerId, NotificationType.TASK_COMPLETED)).isEqualTo(1);
        assertThat(countByType(memberCId, NotificationType.TASK_COMPLETED)).isEqualTo(1);
    }

    @Test
    void alreadyDone_patchedToDoneAgain_noAdditionalNotification() {
        actingAs(ownerId);
        ActionItemDetailResDto item = createTask(memberBId);
        actionItemService.updateActionItem(item.id(),
                new ActionItemUpdateReqDto(null, null, null, null, ActionItemStatus.DONE, null));
        long before = countByType(memberBId, NotificationType.TASK_COMPLETED);

        actionItemService.updateActionItem(item.id(),
                new ActionItemUpdateReqDto(null, null, null, null, ActionItemStatus.DONE, null));

        assertThat(countByType(memberBId, NotificationType.TASK_COMPLETED)).isEqualTo(before);
    }

    @Test
    void manualTaskWithoutOriginMeeting_completed_onlyAssigneeAndCreatorNotified() {
        actingAs(ownerId);
        ActionItemDetailResDto item = createTask(memberBId);

        actingAs(memberBId);
        actionItemService.updateActionItem(item.id(),
                new ActionItemUpdateReqDto(null, null, null, null, ActionItemStatus.DONE, null));

        assertThat(countByType(memberBId, NotificationType.TASK_COMPLETED)).isEqualTo(1);
        assertThat(countByType(ownerId, NotificationType.TASK_COMPLETED)).isEqualTo(1);
        assertThat(notificationsFor(memberCId)).isEmpty();
    }
}
