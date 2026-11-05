package com.talent.management.features.notification.service;

import com.talent.management.features.notification.dto.response.NotificationResponse;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.Role;

import java.util.List;
import java.util.Map;

public interface NotificationService {

    void sendNotification(User recipient, User sender, String title, String message,
                          String type, Long referenceId, String targetUrl);

    void sendToRoles(List<Role> roles, User sender, String title, String message,
                     String type, Long referenceId, String targetUrl);

    List<NotificationResponse> getMyNotifications();

    long getUnreadCount();

    Map<String, Object> getMyNotificationSummary();

    void markAsRead(Long notificationId);

    void markAllAsRead();
}
