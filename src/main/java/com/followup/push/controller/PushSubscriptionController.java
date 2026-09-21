package com.followup.push.controller;

import com.followup.global.security.CurrentUserProvider;
import com.followup.push.dto.PushTokenReqDto;
import com.followup.push.service.PushSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Push")
@RestController
@RequestMapping("/api/push/subscriptions")
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "웹 푸시 구독 등록")
    @ApiResponse(responseCode = "204", description = "등록 성공")
    @PostMapping
    public ResponseEntity<Void> subscribe(@Valid @RequestBody PushTokenReqDto request) {
        pushSubscriptionService.subscribe(currentUserProvider.getCurrentUserId(), request.token());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "웹 푸시 구독 해제")
    @ApiResponse(responseCode = "204", description = "해제 성공(본인 소유가 아니면 조용히 무시)")
    @DeleteMapping
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody PushTokenReqDto request) {
        pushSubscriptionService.unsubscribe(currentUserProvider.getCurrentUserId(), request.token());
        return ResponseEntity.noContent().build();
    }
}
