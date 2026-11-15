package com.talent.management.features.admin_management.dto.request;
import com.talent.management.shared.enums.Role;
import jakarta.validation.constraints.*;
public record ChangeUserRoleRequest(@NotNull Role role, @NotBlank @Size(max = 500) String reason) {}
