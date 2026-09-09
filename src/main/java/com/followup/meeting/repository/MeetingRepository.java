package com.followup.meeting.repository;

import com.followup.meeting.entity.Meeting;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findAllByProjectIdOrderByScheduledAtDesc(Long projectId);

    /** Dashboard 최근 회의 영역을 위해 전체 조회 후 자르지 않고 최신 3건만 DB에서 조회한다. */
    List<Meeting> findTop3ByProjectIdOrderByScheduledAtDesc(Long projectId);

    boolean existsByProjectId(Long projectId);
}
