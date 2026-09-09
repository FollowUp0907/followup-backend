package com.followup.meeting.repository;

import com.followup.meeting.entity.Decision;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionRepository extends JpaRepository<Decision, Long> {

    List<Decision> findAllByMeetingId(Long meetingId);

    /** 같은 시각에 여러 건이 저장될 수 있어 createdAt 대신 id 기준으로 안정적인 생성 순서를 보장한다. */
    List<Decision> findAllByMeetingIdOrderByIdAsc(Long meetingId);

    boolean existsByMeetingId(Long meetingId);
}
