package com.followup.meeting.repository;

import com.followup.meeting.entity.Meeting;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findAllByProjectIdOrderByScheduledAtDesc(Long projectId);

    boolean existsByProjectId(Long projectId);
}
