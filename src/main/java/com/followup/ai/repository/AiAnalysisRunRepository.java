package com.followup.ai.repository;

import com.followup.ai.entity.AiAnalysisRun;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAnalysisRunRepository extends JpaRepository<AiAnalysisRun, Long> {

    List<AiAnalysisRun> findAllByMeetingIdOrderByCreatedAtDesc(Long meetingId);
}
