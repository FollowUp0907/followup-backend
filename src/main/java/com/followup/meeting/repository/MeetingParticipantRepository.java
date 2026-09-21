package com.followup.meeting.repository;

import com.followup.meeting.entity.MeetingParticipant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {

    List<MeetingParticipant> findAllByMeetingId(Long meetingId);

    void deleteAllByMeetingId(Long meetingId);

    void deleteAllByMeetingIdIn(List<Long> meetingIds);

    void deleteAllByUserId(Long userId);
}
