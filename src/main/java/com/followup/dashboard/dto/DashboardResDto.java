package com.followup.dashboard.dto;

import com.followup.actionitem.entity.ActionItemStatus;
import com.followup.actionitem.entity.Priority;
import com.followup.meeting.entity.MeetingStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Dashboard 화면에 필요한 집계 데이터를 하나의 응답으로 전달한다. */
public record DashboardResDto(
        ActionItemSummary actionItemSummary,
        List<DueSoonActionItem> dueSoonActionItems,
        List<RecentMeeting> recentMeetings,
        List<MemberProgress> memberProgress
) {

    public record ActionItemSummary(
            long total,
            long todo,
            long inProgress,
            long done,
            long overdue
    ) {
    }

    public record DueSoonActionItem(
            Long actionItemId,
            String title,
            ActionItemStatus status,
            Priority priority,
            LocalDate dueDate,
            List<Long> assigneeUserIds,
            List<String> assigneeNames
    ) {
    }

    public record RecentMeeting(
            Long meetingId,
            String title,
            LocalDateTime scheduledAt,
            MeetingStatus status
    ) {
    }

    public record MemberProgress(
            Long userId,
            String name,
            long totalCount,
            long doneCount,
            double completionRate
    ) {
    }
}
