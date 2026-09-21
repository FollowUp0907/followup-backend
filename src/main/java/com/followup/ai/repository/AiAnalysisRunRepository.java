package com.followup.ai.repository;

import com.followup.ai.entity.AiAnalysisRun;
import com.followup.ai.entity.AnalysisStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiAnalysisRunRepository extends JpaRepository<AiAnalysisRun, Long> {

    List<AiAnalysisRun> findAllByMeetingIdOrderByCreatedAtDesc(Long meetingId);

    boolean existsByMeetingId(Long meetingId);

    void deleteAllByMeetingIdIn(List<Long> meetingIds);

    /** 회원 탈퇴 시 요청한 사람 참조만 끊는다 — 분석 이력 자체는 그대로 남긴다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AiAnalysisRun a set a.requestedBy = null where a.requestedBy.id = :userId")
    void detachRequestedBy(@Param("userId") Long userId);

    /** 같은 회의·입력 지문·모델/프롬프트 버전의 재사용 가능한 분석을 찾는다. FAILED는 호출 측에서 제외한다. */
    Optional<AiAnalysisRun> findFirstByMeetingIdAndInputHashAndModelNameAndPromptVersionAndStatusInOrderByCreatedAtDesc(
            Long meetingId, String inputHash, String modelName, String promptVersion, List<AnalysisStatus> statuses);
}
