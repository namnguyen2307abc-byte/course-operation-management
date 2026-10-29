package com.talent.management.features.admin_management.dto.response;
import com.talent.management.shared.enums.*;
import java.time.LocalDateTime;
public record AdminUserResponse(Long id, String username, String fullName, String email, String phone,
        Role role, UserStatus status, String subject, boolean mustChangePassword, LocalDateTime createdAt,
        String teachingSubject) {}
