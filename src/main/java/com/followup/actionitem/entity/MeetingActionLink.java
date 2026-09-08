package com.followup.actionitem.entity;

import com.followup.meeting.entity.Meeting;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "meeting_action_links", uniqueConstraints = {
        @UniqueConstraint(name = "uk_meeting_action_links_meeting_action", columnNames = {"meeting_id", "action_item_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingActionLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_item_id", nullable = false)
    private ActionItem actionItem;

    @Column(name = "link_type", nullable = false, length = 20)
    private String linkType;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    @Builder
    public MeetingActionLink(Meeting meeting, ActionItem actionItem, String linkType) {
        this.meeting = meeting;
        this.actionItem = actionItem;
        this.linkType = linkType;
    }

    @PrePersist
    void prePersist() {
        linkedAt = LocalDateTime.now();
    }
}
