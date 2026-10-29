package com.talent.management.features.auth.dto;

import com.talent.management.shared.enums.Role;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Tên đăng nhập không được để trống")
    @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9_.-]{3,100}")
    private String username;

    @NotBlank(message = "Mật khẩu không được để trống")
    @jakarta.validation.constraints.Size(min = 8, max = 72)
    private String password;

    @NotBlank(message = "Họ tên không được để trống")
    @jakarta.validation.constraints.Size(max = 150)
    private String fullName;

    @jakarta.validation.constraints.Email
    @jakarta.validation.constraints.Size(max = 150)
    private String email;
    @jakarta.validation.constraints.Pattern(regexp = "[+0-9 ()-]{0,20}")
    private String phone;
    private Role role;
}
