package com.followup.meeting.entity;

import com.followup.ai.entity.AiAnalysisRun;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "decisions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Decision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_analysis_id")
    private AiAnalysisRun sourceAnalysis;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public Decision(Meeting meeting, String content, AiAnalysisRun sourceAnalysis) {
        this.meeting = meeting;
        this.content = content;
        this.sourceAnalysis = sourceAnalysis;
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
