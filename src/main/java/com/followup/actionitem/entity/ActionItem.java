package com.followup.actionitem.entity;

import com.followup.ai.entity.AiAnalysisRun;
import com.followup.meeting.entity.Meeting;
import com.followup.project.entity.Project;
import com.followup.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "action_items", indexes = {
        @Index(name = "idx_action_items_project_status", columnList = "project_id, status"),
        @Index(name = "idx_action_items_project_due_date", columnList = "project_id, due_date")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_meeting_id")
    private Meeting originMeeting;

    /** 담당자(여러 명 가능). AI 분석 확정 시엔 추천된 한 명만 담기고, 그 외엔 수동으로 여러 명을 지정할 수 있다. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "action_item_assignees",
            joinColumns = @JoinColumn(name = "action_item_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> assignees = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_analysis_id")
    private AiAnalysisRun sourceAnalysis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ActionItemStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private Priority priority;

    @Column(name = "priority_reason", columnDefinition = "TEXT")
    private String priorityReason;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public ActionItem(Project project, Meeting originMeeting, AiAnalysisRun sourceAnalysis,
                       User createdBy, String title, String description, LocalDate dueDate, ActionItemStatus status,
                       Priority priority, String priorityReason, LocalDateTime completedAt) {
        this.project = project;
        this.originMeeting = originMeeting;
        this.sourceAnalysis = sourceAnalysis;
        this.createdBy = createdBy;
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.status = status;
        this.priority = priority;
        this.priorityReason = priorityReason;
        this.completedAt = completedAt;
    }

    /** 전체 교체 방식 — 어떤 사람이 새로 추가/제거됐는지는 호출 측(서비스)에서 미리 diff해 알림에 쓴다. */
    public void replaceAssignees(Set<User> newAssignees) {
        this.assignees.clear();
        this.assignees.addAll(newAssignees);
    }

    /** 프로젝트 멤버 제거 시, 그 사람만 담당자 목록에서 뺀다. */
    public void removeAssignee(Long userId) {
        this.assignees.removeIf(user -> user.getId().equals(userId));
    }

    public void detachOriginMeeting() {
        this.originMeeting = null;
    }

    public void update(String title, String description, LocalDate dueDate, Priority priority) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (dueDate != null) {
            this.dueDate = dueDate;
        }
        if (priority != null) {
            this.priority = priority;
        }
    }

    /** DONE 전환 시 completedAt을 설정하고, DONE 해제 시 null로 되돌린다. */
    public void changeStatus(ActionItemStatus newStatus) {
        this.status = newStatus;
        this.completedAt = newStatus == ActionItemStatus.DONE ? LocalDateTime.now() : null;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
