package com.followup.meeting.dto;

import com.followup.meeting.entity.MeetingParticipant;

public record MeetingParticipantResDto(
        Long userId,
        String name,
        String email
) {

    public static MeetingParticipantResDto from(MeetingParticipant participant) {
        return new MeetingParticipantResDto(
                participant.getUser().getId(),
                participant.getUser().getName(),
                participant.getUser().getEmail()
        );
    }
}
