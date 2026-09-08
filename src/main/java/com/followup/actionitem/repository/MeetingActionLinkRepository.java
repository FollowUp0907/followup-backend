package com.followup.actionitem.repository;

import com.followup.actionitem.entity.MeetingActionLink;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingActionLinkRepository extends JpaRepository<MeetingActionLink, Long> {

    List<MeetingActionLink> findAllByMeetingId(Long meetingId);

    void deleteAllByMeetingId(Long meetingId);

    void deleteAllByActionItemId(Long actionItemId);
}
