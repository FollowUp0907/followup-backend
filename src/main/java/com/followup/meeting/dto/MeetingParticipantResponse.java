package com.followup.meeting.dto;

import com.followup.meeting.entity.MeetingParticipant;

public record MeetingParticipantResponse(
        Long userId,
        String name,
        String email
) {

    public static MeetingParticipantResponse from(MeetingParticipant participant) {
        return new MeetingParticipantResponse(
                participant.getUser().getId(),
                participant.getUser().getName(),
                participant.getUser().getEmail()
        );
    }
}
