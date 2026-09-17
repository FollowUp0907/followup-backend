package com.followup.notification.controller;

import com.followup.notification.dto.NotificationResDto;
import com.followup.notification.dto.UnreadCountResDto;
import com.followup.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

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
}
