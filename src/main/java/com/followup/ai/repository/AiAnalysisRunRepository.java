package com.followup.ai.repository;

import com.followup.ai.entity.AiAnalysisRun;
import com.followup.ai.entity.AnalysisStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAnalysisRunRepository extends JpaRepository<AiAnalysisRun, Long> {

    List<AiAnalysisRun> findAllByMeetingIdOrderByCreatedAtDesc(Long meetingId);

    boolean existsByMeetingId(Long meetingId);

    void deleteAllByMeetingIdIn(List<Long> meetingIds);

    /** 같은 회의·입력 지문·모델/프롬프트 버전의 재사용 가능한 분석을 찾는다. FAILED는 호출 측에서 제외한다. */
    Optional<AiAnalysisRun> findFirstByMeetingIdAndInputHashAndModelNameAndPromptVersionAndStatusInOrderByCreatedAtDesc(
            Long meetingId, String inputHash, String modelName, String promptVersion, List<AnalysisStatus> statuses);
}
