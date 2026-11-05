package com.talent.management.features.notification.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private Long id;
    private String title;
    private String message;
    private String type;
    private Long referenceId;
    private String targetUrl;
    private Boolean isRead;
    private String senderName;
    private LocalDateTime createdAt;
    private String createdAtText;
}
