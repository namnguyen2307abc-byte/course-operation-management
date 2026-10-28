package com.talent.management.features.admin_management.dto.request;
import jakarta.validation.constraints.*;
public record ResetUserPasswordRequest(@NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 500) String reason) {}
