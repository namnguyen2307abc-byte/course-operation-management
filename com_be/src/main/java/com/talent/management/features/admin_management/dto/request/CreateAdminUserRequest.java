package com.talent.management.features.admin_management.dto.request;

import com.talent.management.shared.enums.Role;
import jakarta.validation.constraints.*;

public record CreateAdminUserRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,100}") String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 150) String fullName,
        @Email @Size(max = 150) String email,
        @Pattern(regexp = "[+0-9 ()-]{0,20}") String phone,
        @NotNull Role role,
        @Size(max = 100) String subject,
        Long studentId) {}
