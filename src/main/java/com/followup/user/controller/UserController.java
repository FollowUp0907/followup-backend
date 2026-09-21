package com.followup.user.controller;

import com.followup.user.dto.MeResDto;
import com.followup.user.dto.UpdateMeReqDto;
import com.followup.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User")
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 수정(이름 변경)")
    @PatchMapping
    public ResponseEntity<MeResDto> updateMe(@Valid @RequestBody UpdateMeReqDto request) {
        return ResponseEntity.ok(userService.updateMe(request));
    }

    @Operation(summary = "회원 탈퇴")
    @ApiResponse(responseCode = "204", description = "탈퇴 성공")
    @DeleteMapping
    public ResponseEntity<Void> deleteMe() {
        userService.deleteMe();
        return ResponseEntity.noContent().build();
    }
}
