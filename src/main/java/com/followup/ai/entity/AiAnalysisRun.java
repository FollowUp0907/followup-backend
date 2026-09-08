package com.followup.ai.entity;

import com.followup.meeting.entity.Meeting;
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
import jakarta.persistence.Table;
import java.time.LocalDateTime;

import lombok.*;

@Entity
@Table(name = "ai_analysis_runs", indexes = {
        @Index(name = "idx_ai_analysis_runs_meeting_created", columnList = "meeting_id, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiAnalysisRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisStatus status;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "prompt_version", length = 30)
    private String promptVersion;

    @Column(name = "draft_json", columnDefinition = "JSON")
    private String draftJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Builder
    public AiAnalysisRun(Meeting meeting, User requestedBy, AnalysisStatus status, String modelName,
                          String promptVersion, String draftJson, String errorMessage, LocalDateTime confirmedAt) {
        this.meeting = meeting;
        this.requestedBy = requestedBy;
        this.status = status;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.draftJson = draftJson;
        this.errorMessage = errorMessage;
        this.confirmedAt = confirmedAt;
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
