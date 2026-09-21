package com.followup.invitation.controller;

import com.followup.global.security.CurrentUserProvider;
import com.followup.invitation.dto.CreateInvitationReqDto;
import com.followup.invitation.dto.InvitationResDto;
import com.followup.invitation.dto.InvitationViewResDto;
import com.followup.invitation.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Invitation")
@RestController
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "프로젝트 멤버 이메일 초대")
    @ApiResponse(responseCode = "201", description = "초대 생성 성공")
    @ApiResponse(responseCode = "409", description = "이미 멤버이거나 살아있는 초대가 이미 있음")
    @PostMapping("/api/project/{projectId}/invitations")
    public ResponseEntity<InvitationResDto> createInvitation(@PathVariable Long projectId,
                                                               @Valid @RequestBody CreateInvitationReqDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invitationService.createInvitation(projectId, request));
    }

    @Operation(summary = "프로젝트 초대 목록 조회")
    @GetMapping("/api/project/{projectId}/invitations")
    public ResponseEntity<List<InvitationResDto>> listInvitations(@PathVariable Long projectId) {
        return ResponseEntity.ok(invitationService.listInvitations(projectId));
    }

    @Operation(summary = "초대 재발송")
    @ApiResponse(responseCode = "204", description = "재발송 성공")
    @PostMapping("/api/project/{projectId}/invitations/{invitationId}/resend")
    public ResponseEntity<Void> resendInvitation(@PathVariable Long projectId, @PathVariable Long invitationId) {
        invitationService.resendInvitation(projectId, invitationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "초대 취소")
    @ApiResponse(responseCode = "204", description = "취소 성공")
    @DeleteMapping("/api/project/{projectId}/invitations/{invitationId}")
    public ResponseEntity<Void> cancelInvitation(@PathVariable Long projectId, @PathVariable Long invitationId) {
        invitationService.cancelInvitation(projectId, invitationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "초대 내용 확인 (인증 불필요)")
    @SecurityRequirements
    @GetMapping("/api/invitations/{token}")
    public ResponseEntity<InvitationViewResDto> viewInvitation(@PathVariable String token) {
        return ResponseEntity.ok(invitationService.viewInvitation(token));
    }

    @Operation(summary = "초대 수락")
    @PostMapping("/api/invitations/{token}/accept")
    public ResponseEntity<InvitationResDto> acceptInvitation(@PathVariable String token) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(invitationService.acceptInvitation(token, currentUserId));
    }
}
