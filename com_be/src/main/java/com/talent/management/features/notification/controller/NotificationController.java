package com.talent.management.features.notification.controller;

import com.talent.management.features.notification.dto.response.NotificationResponse;
import com.talent.management.features.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "2b. Thông báo hệ thống (Notifications)",
     description = "Quản lý thông báo thời gian thực cho Phụ huynh, Giáo viên, Nhân viên và Quản trị viên")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Lấy danh sách thông báo và số lượng chưa đọc của người dùng hiện tại")
    @GetMapping
    public ResponseEntity<Map<String, Object>> getMyNotifications() {
        return ResponseEntity.ok(notificationService.getMyNotificationSummary());
    }

    @Operation(summary = "Lấy danh sách thông báo dạng mảng")
    @GetMapping("/list")
    public ResponseEntity<List<NotificationResponse>> getMyNotificationList() {
        return ResponseEntity.ok(notificationService.getMyNotifications());
    }

    @Operation(summary = "Lấy số lượng thông báo chưa đọc")
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("unreadCount", notificationService.getUnreadCount()));
    }

    @Operation(summary = "Đánh dấu 1 thông báo là đã đọc")
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Đánh dấu tất cả thông báo là đã đọc")
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok().build();
    }
}
