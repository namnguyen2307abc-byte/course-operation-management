package com.talent.management.features.auth.controller;

import com.talent.management.features.auth.dto.AuthResponse;
import com.talent.management.features.auth.dto.LoginRequest;
import com.talent.management.features.auth.dto.RegisterRequest;
import com.talent.management.features.auth.dto.UserDto;
import com.talent.management.features.auth.service.AuthService;
import com.talent.management.shared.enums.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication & Users", description = "Đăng nhập, đăng ký tài khoản (Phụ huynh, Giáo viên, Admin) và quản lý người dùng")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập hệ thống", description = "Đăng nhập với username và password, trả về JWT Token và thông tin phân quyền")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản mới", description = "Đăng ký tài khoản Phụ huynh hoặc Học viên tự do")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @GetMapping("/users")
    @Operation(summary = "Lấy danh sách người dùng theo Role", description = "Lọc danh sách giáo viên, phụ huynh hoặc nhân viên")
    public ResponseEntity<List<UserDto>> getUsersByRole(@RequestParam(required = false) Role role) {
        return ResponseEntity.ok(authService.getUsersByRole(role));
    }
}
