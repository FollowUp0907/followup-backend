package com.followup.dashboard.service;

import com.followup.actionitem.entity.ActionItem;
import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.dashboard.dto.DashboardResDto;
import com.followup.dashboard.dto.DashboardResDto.ActionItemSummary;
import com.followup.dashboard.dto.DashboardResDto.DueSoonActionItem;
import com.followup.dashboard.dto.DashboardResDto.MemberProgress;
import com.followup.dashboard.dto.DashboardResDto.RecentMeeting;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.meeting.entity.Meeting;
import com.followup.meeting.repository.MeetingRepository;
import com.followup.project.entity.Project;
import com.followup.project.entity.ProjectMember;
import com.followup.project.repository.ProjectMemberRepository;
import com.followup.project.repository.ProjectRepository;
import com.followup.user.entity.User;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 대시보드 조회를 담당한다.
 * ActionItem/Meeting/ProjectMember 데이터를 조합해 화면에 필요한 집계 정보를 구성한다.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int DUE_SOON_DAYS = 3;
    private static final int DUE_SOON_LIMIT = 5;

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ActionItemRepository actionItemRepository;
    private final MeetingRepository meetingRepository;
    private final CurrentUserProvider currentUserProvider;

    /**
     * 해당 프로젝트의 멤버만 조회할 수 있다.
     * 업무 요약, 마감 임박 업무, 최근 회의, 멤버별 진행률을 하나의 응답으로 구성한다.
     */
    @Transactional(readOnly = true)
    public DashboardResDto getDashboard(Long projectId) {
        getProjectOrThrow(projectId);
        requireMember(projectId, currentUserProvider.getCurrentUserId());

        List<ActionItem> actionItems = actionItemRepository.findAllByProjectIdFetchAssignee(projectId);
        LocalDate today = LocalDate.now();

        ActionItemSummary summary = buildSummary(actionItems, today);
        List<DueSoonActionItem> dueSoonActionItems = buildDueSoon(actionItems, today);
        List<RecentMeeting> recentMeetings = meetingRepository.findTop3ByProjectIdOrderByScheduledAtDesc(projectId)
                .stream()
                .map(DashboardService::toRecentMeeting)
                .toList();
        List<MemberProgress> memberProgress = buildMemberProgress(projectId, actionItems);

        return new DashboardResDto(summary, dueSoonActionItems, recentMeetings, memberProgress);
    }

    private ActionItemSummary buildSummary(List<ActionItem> actionItems, LocalDate today) {
        long total = actionItems.size();
        long todo = actionItems.stream().filter(item -> item.getStatus() == ActionItemStatus.TODO).count();
        long inProgress = actionItems.stream().filter(item -> item.getStatus() == ActionItemStatus.IN_PROGRESS).count();
        long done = actionItems.stream().filter(item -> item.getStatus() == ActionItemStatus.DONE).count();
        long overdue = actionItems.stream().filter(item -> isOverdue(item, today)).count();
        return new ActionItemSummary(total, todo, inProgress, done, overdue);
    }

    /** 오늘 이전에 마감되었고 완료되지 않은 업무를 지연 업무로 판단한다. */
    private boolean isOverdue(ActionItem item, LocalDate today) {
        return item.getDueDate() != null
                && item.getDueDate().isBefore(today)
                && item.getStatus() != ActionItemStatus.DONE;
    }

    /** 미완료(TODO/IN_PROGRESS) 업무 중 dueDate가 오늘부터 3일 이내인 것만 마감 임박 업무로 간주한다. */
    private List<DueSoonActionItem> buildDueSoon(List<ActionItem> actionItems, LocalDate today) {
        LocalDate dueSoonLimit = today.plusDays(DUE_SOON_DAYS);
        return actionItems.stream()
                .filter(item -> isActive(item))
                .filter(item -> item.getDueDate() != null)
                .filter(item -> !item.getDueDate().isBefore(today) && !item.getDueDate().isAfter(dueSoonLimit))
                .sorted(Comparator.comparing(ActionItem::getDueDate))
                .limit(DUE_SOON_LIMIT)
                .map(DashboardService::toDueSoonActionItem)
                .toList();
    }

    private boolean isActive(ActionItem item) {
        return item.getStatus() == ActionItemStatus.TODO || item.getStatus() == ActionItemStatus.IN_PROGRESS;
    }

    /**
     * 프로젝트 전체 멤버를 기준으로 업무 진행률을 집계한다.
     * 담당 업무가 없는 멤버도 0건으로 포함하며, 미배정 업무는 제외한다.
     */
    private List<MemberProgress> buildMemberProgress(Long projectId, List<ActionItem> actionItems) {
        Map<Long, List<ActionItem>> byAssignee = actionItems.stream()
                .filter(item -> item.getAssignee() != null)
                .collect(Collectors.groupingBy(item -> item.getAssignee().getId()));

        List<ProjectMember> members = projectMemberRepository.findAllByProjectId(projectId);
        return members.stream()
                .map(member -> toMemberProgress(member, byAssignee.getOrDefault(member.getUser().getId(), List.of())))
                .toList();
    }

    private static MemberProgress toMemberProgress(ProjectMember member, List<ActionItem> assigned) {
        User user = member.getUser();
        long total = assigned.size();
        long done = assigned.stream().filter(item -> item.getStatus() == ActionItemStatus.DONE).count();
        double completionRate = total == 0 ? 0.0 : (double) done / total;
        return new MemberProgress(user.getId(), user.getName(), total, done, completionRate);
    }

    private static DueSoonActionItem toDueSoonActionItem(ActionItem item) {
        User assignee = item.getAssignee();
        return new DueSoonActionItem(
                item.getId(),
                item.getTitle(),
                item.getStatus(),
                item.getPriority(),
                item.getDueDate(),
                assignee != null ? assignee.getId() : null,
                assignee != null ? assignee.getName() : null
        );
    }

    private static RecentMeeting toRecentMeeting(Meeting meeting) {
        return new RecentMeeting(meeting.getId(), meeting.getTitle(), meeting.getScheduledAt(), meeting.getStatus());
    }

    private Project getProjectOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private void requireMember(Long projectId, Long userId) {
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
    }
}
