package com.talent.management.features.notification.service.impl;

import com.talent.management.features.notification.dto.response.NotificationResponse;
import com.talent.management.features.attendance_makeup.exception.AttendanceMakeupException;
import com.talent.management.features.notification.repository.NotificationRepository;
import com.talent.management.features.notification.service.NotificationService;
import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.shared.entity.Notification;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @Override
    @Transactional
    public void sendNotification(User recipient, User sender, String title, String message,
                                 String type, Long referenceId, String targetUrl) {
        if (recipient == null || recipient.getId() == null) {
            return;
        }
        try {
            Notification notification = Notification.builder()
                    .recipient(recipient)
                    .sender(sender)
                    .title(title)
                    .message(message)
                    .type(type != null ? type : "SYSTEM")
                    .referenceId(referenceId)
                    .targetUrl(targetUrl != null ? targetUrl : "/pages/absence-makeup.html")
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(notification);
        } catch (Exception e) {
            log.warn("Không thể lưu thông báo cho user #{}: {}", recipient.getId(), e.getMessage());
        }
    }

    @Override
    @Transactional
    public void sendToRoles(List<Role> roles, User sender, String title, String message,
                            String type, Long referenceId, String targetUrl) {
        if (roles == null || roles.isEmpty()) {
            return;
        }
        try {
            List<User> recipients = userRepository.findAll().stream()
                    .filter(u -> u.getRole() != null && roles.contains(u.getRole()))
                    .toList();
            for (User recipient : recipients) {
                sendNotification(recipient, sender, title, message, type, referenceId, targetUrl);
            }
        } catch (Exception e) {
            log.warn("Không thể gửi thông báo tới nhóm role {}: {}", roles, e.getMessage());
        }
    }

    @Override
    public List<NotificationResponse> getMyNotifications() {
        User currentUser = currentUserService.getCurrentUser();
        return notificationRepository.findTop50ByRecipientIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public long getUnreadCount() {
        User currentUser = currentUserService.getCurrentUser();
        return notificationRepository.countByRecipientIdAndIsReadFalse(currentUser.getId());
    }

    @Override
    public Map<String, Object> getMyNotificationSummary() {
        User currentUser = currentUserService.getCurrentUser();
        List<NotificationResponse> items = notificationRepository
                .findTop50ByRecipientIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(this::toResponse)
                .toList();
        long unreadCount = items.stream().filter(i -> !Boolean.TRUE.equals(i.getIsRead())).count();

        Map<String, Object> result = new HashMap<>();
        result.put("unreadCount", unreadCount);
        result.put("notifications", items);
        return result;
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId) {
        User currentUser = currentUserService.getCurrentUser();
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy thông báo với ID: " + notificationId));
        if (n.getRecipient() != null && n.getRecipient().getId().equals(currentUser.getId())) {
            n.setIsRead(true);
            notificationRepository.save(n);
        }
    }

    @Override
    @Transactional
    public void markAllAsRead() {
        User currentUser = currentUserService.getCurrentUser();
        List<Notification> unread = notificationRepository.findByRecipientIdAndIsReadFalse(currentUser.getId());
        for (Notification n : unread) {
            n.setIsRead(true);
        }
        if (!unread.isEmpty()) {
            notificationRepository.saveAll(unread);
        }
    }

    private NotificationResponse toResponse(Notification n) {
        String senderName = n.getSender() != null
                ? (n.getSender().getFullName() != null ? n.getSender().getFullName() : n.getSender().getUsername())
                : "Hệ thống";
        String createdAtText = n.getCreatedAt() != null ? n.getCreatedAt().format(DT_FMT) : "";
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .referenceId(n.getReferenceId())
                .targetUrl(n.getTargetUrl())
                .isRead(Boolean.TRUE.equals(n.getIsRead()))
                .senderName(senderName)
                .createdAt(n.getCreatedAt())
                .createdAtText(createdAtText)
                .build();
    }
}
