package com.followup.notification.entity;

import com.followup.actionitem.entity.ActionItem;
import com.followup.project.entity.Project;
import com.followup.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user_remind_at", columnList = "user_id, remind_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_item_id", nullable = false)
    private ActionItem actionItem;

    @Column(name = "task_title", nullable = false, length = 255)
    private String taskTitle;

    @Column(name = "remind_at", nullable = false)
    private LocalDateTime remindAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Builder
    public Notification(User user, Project project, ActionItem actionItem, String taskTitle, LocalDateTime remindAt) {
        this.user = user;
        this.project = project;
        this.actionItem = actionItem;
        this.taskTitle = taskTitle;
        this.remindAt = remindAt;
    }

    public void markAsRead() {
        this.readAt = LocalDateTime.now();
    }

    /** 같은 업무에 알림을 다시 설정할 때 덮어쓰며, 다시 도착 전 상태로 되돌리기 위해 readAt을 초기화한다. */
    public void update(String taskTitle, LocalDateTime remindAt) {
        this.taskTitle = taskTitle;
        this.remindAt = remindAt;
        this.readAt = null;
    }

    /** 알림을 설정한 사람이 바뀌면(같은 업무를 다른 사람이 다시 설정) 소유자를 그 사람으로 교체한다. */
    public void reassignTo(User user) {
        this.user = user;
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
