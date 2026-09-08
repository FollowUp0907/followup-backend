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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "action_items", indexes = {
        @Index(name = "idx_action_items_project_status", columnList = "project_id, status"),
        @Index(name = "idx_action_items_project_due_date", columnList = "project_id, due_date"),
        @Index(name = "idx_action_items_assignee_status", columnList = "assignee_user_id, status")
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_user_id")
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_analysis_id")
    private AiAnalysisRun sourceAnalysis;

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
    public ActionItem(Project project, Meeting originMeeting, User assignee, AiAnalysisRun sourceAnalysis,
                       String title, String description, LocalDate dueDate, ActionItemStatus status,
                       Priority priority, String priorityReason, LocalDateTime completedAt) {
        this.project = project;
        this.originMeeting = originMeeting;
        this.assignee = assignee;
        this.sourceAnalysis = sourceAnalysis;
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.status = status;
        this.priority = priority;
        this.priorityReason = priorityReason;
        this.completedAt = completedAt;
    }

    public void unassign() {
        this.assignee = null;
    }

    public void assignTo(User user) {
        this.assignee = user;
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
