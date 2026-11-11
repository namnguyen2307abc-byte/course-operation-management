package com.talent.management.features.admin_management.dto.response;
import java.time.LocalDateTime;
public record AdminAuditResponse(Long id, Long actorId, String actorUsername, Long targetId,
        String targetUsername, String action, String details, LocalDateTime createdAt) {}
