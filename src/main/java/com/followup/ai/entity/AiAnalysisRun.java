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

    /** 요청한 사용자가 탈퇴하면 null이 된다(회원 탈퇴 시 분석 이력 자체는 유지). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisStatus status;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "prompt_version", length = 30)
    private String promptVersion;

    // content+scheduledAt을 SHA-256으로 지문화한 값. 중복 분석 감지에 쓰이며 원문은 저장하지 않는다.
    @Column(name = "input_hash", length = 64)
    private String inputHash;

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
                          String promptVersion, String inputHash, String draftJson, String errorMessage,
                          LocalDateTime confirmedAt) {
        this.meeting = meeting;
        this.requestedBy = requestedBy;
        this.status = status;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.inputHash = inputHash;
        this.draftJson = draftJson;
        this.errorMessage = errorMessage;
        this.confirmedAt = confirmedAt;
    }

    public void markGenerated(String draftJson) {
        this.status = AnalysisStatus.GENERATED;
        this.draftJson = draftJson;
    }

    /** row는 삭제하지 않고 상태만 FAILED로 남겨 재시도·이력 확인이 가능하게 한다. */
    public void markFailed(String errorMessage) {
        this.status = AnalysisStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    /** GENERATED 상태에서만 유효한 확정 전이다(조건은 AiAnalysisService에서 검사). */
    public void confirm() {
        this.status = AnalysisStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
