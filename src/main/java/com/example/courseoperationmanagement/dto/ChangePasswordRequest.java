package com.example.courseoperationmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required.")
    private String currentPassword;

    @NotBlank(message = "New password is required.")
    @Size(min = 8, max = 64, message = "New password must be between 8 and 64 characters.")
    private String newPassword;

    @NotBlank(message = "Please confirm your new password.")
    private String confirmPassword;
}
