package com.talent.management.features.admin_management.dto.request;
import jakarta.validation.constraints.*;

public record UpdateAdminUserRequest(@NotBlank @Size(max = 150) String fullName,
        @Email @Size(max = 150) String email,
        @Pattern(regexp = "[+0-9 ()-]{0,20}") String phone,
        @Size(max = 100) String subject) {}
