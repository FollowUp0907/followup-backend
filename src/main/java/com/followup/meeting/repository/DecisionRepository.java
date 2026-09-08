package com.followup.meeting.repository;

import com.followup.meeting.entity.Decision;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionRepository extends JpaRepository<Decision, Long> {

    List<Decision> findAllByMeetingId(Long meetingId);
}
