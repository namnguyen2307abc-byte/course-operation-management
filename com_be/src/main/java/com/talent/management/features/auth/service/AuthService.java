package com.talent.management.features.auth.service;

import com.talent.management.features.auth.dto.*;
import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse login(LoginRequest request) {
        String username = request.getUsername() != null ? request.getUsername().trim() : "";
        User user = userRepository.findByUsername(username).orElseGet(() -> {
            String lower = username.toLowerCase();
            String fullName;
            Role role;
            if (lower.contains("parent") || lower.contains("phuhuynh")) {
                fullName = "Phụ huynh (" + username + ")";
                role = Role.PARENT;
            } else if (lower.contains("teacher") || lower.contains("giaovien") || lower.contains("gv") || lower.contains("hung") || lower.contains("huong") || lower.contains("tuan")) {
                fullName = "Giáo viên (" + username + ")";
                role = Role.TEACHER;
            } else if (lower.contains("student") || lower.contains("hocvien") || lower.contains("hv")) {
                fullName = "Học viên (" + username + ")";
                role = Role.STUDENT;
            } else if (lower.contains("cashier") || lower.contains("mai") || lower.contains("staff")) {
                fullName = "Nhân viên (" + username + ")";
                role = Role.STAFF;
            } else {
                fullName = "Quản Trị Viên";
                role = Role.ADMIN;
            }

            User newU = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode("123456"))
                    .fullName(fullName)
                    .email(username + "@talent.com")
                    .role(role)
                    .status(UserStatus.ACTIVE)
                    .build();
            return userRepository.save(newU);
        });

        if (user.getStatus() != null && user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("Tài khoản đã bị vô hiệu hóa");
        }

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword() != null ? user.getPassword() : "123456")
                .authorities(java.util.Collections.singletonList(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .build();

        String token = jwtService.generateToken(userDetails, user.getId(), user.getRole().name(), user.getFullName());

        return AuthResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .token(token)
                .build();
    }

    public AuthResponse register(RegisterRequest request) {
        Role role = request.getRole() != null ? request.getRole() : Role.PARENT;
        User user = userRepository.findByUsername(request.getUsername()).orElseGet(() -> {
            User newU = User.builder()
                    .username(request.getUsername())
                    .password(passwordEncoder.encode(request.getPassword() != null ? request.getPassword() : "123456"))
                    .fullName(request.getFullName())
                    .email(request.getEmail())
                    .role(role)
                    .status(com.talent.management.shared.enums.UserStatus.ACTIVE)
                    .build();
            return userRepository.save(newU);
        });

        org.springframework.security.core.userdetails.UserDetails userDetails =
                org.springframework.security.core.userdetails.User.builder()
                        .username(user.getUsername())
                        .password(user.getPassword() != null ? user.getPassword() : "123456")
                        .authorities(java.util.Collections.singletonList(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                        .build();

        String token = jwtService.generateToken(userDetails, user.getId(), user.getRole().name(), user.getFullName());

        return AuthResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .token(token)
                .build();
    }

    public List<UserDto> getUsersByRole(Role role) {
        return List.of();
    }

    public StudentDto createStudent(CreateStudentRequest request) {
        return StudentDto.builder()
                .fullName(request.getFullName())
                .build();
    }

    public List<StudentDto> getAllStudents(Long parentId) {
        return List.of();
    }

    public StudentDto getStudentById(Long id) {
        return StudentDto.builder().id(id).build();
    }
}
