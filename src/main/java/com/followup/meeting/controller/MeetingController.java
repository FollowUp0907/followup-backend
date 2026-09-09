package com.followup.meeting.controller;

import com.followup.meeting.dto.MeetingCreateReqDto;
import com.followup.meeting.dto.MeetingDetailResDto;
import com.followup.meeting.dto.MeetingListResDto;
import com.followup.meeting.dto.MeetingUpdateReqDto;
import com.followup.meeting.service.MeetingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    @GetMapping("/project/{projectId}/meetings")
    public ResponseEntity<List<MeetingListResDto>> getMeetings(@PathVariable Long projectId) {
        return ResponseEntity.ok(meetingService.getMeetings(projectId));
    }

    @PostMapping("/project/{projectId}/meeting")
    public ResponseEntity<MeetingDetailResDto> createMeeting(@PathVariable Long projectId,
                                                                @Valid @RequestBody MeetingCreateReqDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(meetingService.createMeeting(projectId, request));
    }

    @GetMapping("/meeting/{meetingId}")
    public ResponseEntity<MeetingDetailResDto> getMeeting(@PathVariable Long meetingId) {
        return ResponseEntity.ok(meetingService.getMeeting(meetingId));
    }

    @PatchMapping("/meeting/{meetingId}")
    public ResponseEntity<MeetingDetailResDto> updateMeeting(@PathVariable Long meetingId,
                                                                @Valid @RequestBody MeetingUpdateReqDto request) {
        return ResponseEntity.ok(meetingService.updateMeeting(meetingId, request));
    }

    @DeleteMapping("/meeting/{meetingId}")
    public ResponseEntity<Void> deleteMeeting(@PathVariable Long meetingId) {
        meetingService.deleteMeeting(meetingId);
        return ResponseEntity.noContent().build();
    }
}
