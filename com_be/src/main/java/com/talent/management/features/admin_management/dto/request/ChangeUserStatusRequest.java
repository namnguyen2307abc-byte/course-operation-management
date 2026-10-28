package com.talent.management.features.admin_management.dto.request;
import com.talent.management.shared.enums.UserStatus;
import jakarta.validation.constraints.*;
public record ChangeUserStatusRequest(@NotNull UserStatus status, @NotBlank @Size(max = 500) String reason) {}
