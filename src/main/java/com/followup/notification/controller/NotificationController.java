package com.followup.notification.controller;

import com.followup.global.security.CurrentUserProvider;
import com.followup.notification.dto.NotificationResDto;
import com.followup.notification.dto.UnreadCountResDto;
import com.followup.notification.service.NotificationService;
import com.followup.notification.sse.SseEmitterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Notification")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "알림 목록 조회")
    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationResDto>> getNotifications(
            @RequestParam(required = false, defaultValue = "false") boolean due) {
        return ResponseEntity.ok(notificationService.getNotifications(due));
    }

    @Operation(summary = "읽지 않은 알림 개수 조회")
    @GetMapping("/notifications/unread-count")
    public ResponseEntity<UnreadCountResDto> getUnreadCount() {
        return ResponseEntity.ok(new UnreadCountResDto(notificationService.getUnreadCount()));
    }

    @Operation(summary = "알림 삭제")
    @ApiResponse(responseCode = "204", description = "삭제 성공")
    @DeleteMapping("/notification/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "알림 읽음 처리")
    @PatchMapping("/notification/{id}/read")
    public ResponseEntity<NotificationResDto> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(id));
    }

    @Operation(summary = "전체 알림 읽음 처리")
    @ApiResponse(responseCode = "204", description = "처리 성공")
    @PatchMapping("/notifications/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.noContent().build();
    }

    /**
     * 알림 실시간 스트림(SSE). EventSource는 커스텀 헤더를 못 실으므로 이 경로는 쿼리 파라미터 토큰
     * 인증도 허용된다(JwtAuthenticationFilter 참고). 폴링을 대체하지 않고 추가되는 채널이다.
     */
    @Operation(summary = "알림 실시간 스트림 (SSE)")
    @GetMapping(value = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        Long userId = currentUserProvider.getCurrentUserId();
        SseEmitter emitter = new SseEmitter(0L);
        sseEmitterRegistry.register(userId, emitter);
        emitter.onCompletion(() -> sseEmitterRegistry.remove(userId, emitter));
        emitter.onTimeout(() -> sseEmitterRegistry.remove(userId, emitter));
        emitter.onError(e -> sseEmitterRegistry.remove(userId, emitter));
        return emitter;
    }
}
