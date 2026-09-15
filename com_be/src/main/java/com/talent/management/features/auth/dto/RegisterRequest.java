package com.talent.management.features.auth.dto;

import com.talent.management.shared.enums.Role;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Tên đăng nhập không được để trống")
    private String username;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;

    @NotBlank(message = "Họ tên không được để trống")
    private String fullName;

    private String email;
    private String phone;
    private Role role;
}
