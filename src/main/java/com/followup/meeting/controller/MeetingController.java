package com.followup.meeting.controller;

import com.followup.meeting.dto.MeetingCreateReqDto;
import com.followup.meeting.dto.MeetingDetailResDto;
import com.followup.meeting.dto.MeetingListResDto;
import com.followup.meeting.dto.MeetingUpdateReqDto;
import com.followup.meeting.service.MeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Meeting")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    @Operation(summary = "회의 목록 조회")
    @GetMapping("/project/{projectId}/meetings")
    public ResponseEntity<List<MeetingListResDto>> getMeetings(@PathVariable Long projectId) {
        return ResponseEntity.ok(meetingService.getMeetings(projectId));
    }

    @Operation(summary = "회의 생성")
    @ApiResponse(responseCode = "201", description = "생성 성공")
    @PostMapping("/project/{projectId}/meeting")
    public ResponseEntity<MeetingDetailResDto> createMeeting(@PathVariable Long projectId,
                                                                @Valid @RequestBody MeetingCreateReqDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(meetingService.createMeeting(projectId, request));
    }

    @Operation(summary = "회의 상세 조회")
    @GetMapping("/meeting/{meetingId}")
    public ResponseEntity<MeetingDetailResDto> getMeeting(@PathVariable Long meetingId) {
        return ResponseEntity.ok(meetingService.getMeeting(meetingId));
    }

    @Operation(summary = "회의 수정")
    @PatchMapping("/meeting/{meetingId}")
    public ResponseEntity<MeetingDetailResDto> updateMeeting(@PathVariable Long meetingId,
                                                                @Valid @RequestBody MeetingUpdateReqDto request) {
        return ResponseEntity.ok(meetingService.updateMeeting(meetingId, request));
    }

    @Operation(summary = "회의 삭제")
    @ApiResponse(responseCode = "204", description = "삭제 성공")
    @ApiResponse(responseCode = "409", description = "AI 분석 또는 결정 이력이 있어 삭제할 수 없음")
    @DeleteMapping("/meeting/{meetingId}")
    public ResponseEntity<Void> deleteMeeting(@PathVariable Long meetingId) {
        meetingService.deleteMeeting(meetingId);
        return ResponseEntity.noContent().build();
    }
}
