package com.followup.ai.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 분석 상태: 분석 중(PROCESSING) → 생성됨(GENERATED) → 확정됨(CONFIRMED), 실패 시 FAILED(재시도 가능)")
public enum AnalysisStatus {
    PROCESSING,
    GENERATED,
    CONFIRMED,
    FAILED
}
