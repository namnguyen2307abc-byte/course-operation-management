package com.talent.management.features.auth.service;

import com.talent.management.features.auth.dto.*;
import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new BadCredentialsException("Thông tin đăng nhập không hợp lệ"));
        if (user.getStatus() != UserStatus.ACTIVE || request.getPassword() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Thông tin đăng nhập không hợp lệ");
        }
        return response(user);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getRole() != null && request.getRole() != Role.PARENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đăng ký công khai chỉ dành cho phụ huynh");
        }
        String username = request.getUsername().trim();
        if (!username.matches("[A-Za-z0-9_.-]{3,100}") || request.getFullName().trim().length() > 150
                || request.getPassword().length() < 8 || request.getPassword().length() > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thông tin tài khoản không hợp lệ; mật khẩu cần từ 8 đến 72 ký tự");
        }
        if (userRepository.existsByUsername(username) || (request.getEmail() != null
                && !request.getEmail().isBlank() && userRepository.existsByEmail(request.getEmail().trim()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập hoặc email đã được sử dụng");
        }
        User user = userRepository.save(User.builder().username(username)
                .password(passwordEncoder.encode(request.getPassword())).fullName(request.getFullName().trim())
                .email(request.getEmail()).phone(request.getPhone()).role(Role.PARENT).status(UserStatus.ACTIVE).build());
        return response(user);
    }

    @Transactional
    public AuthResponse changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findLockedByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Không tìm thấy tài khoản"));
        if (user.getStatus() != UserStatus.ACTIVE || !passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadCredentialsException("Mật khẩu hiện tại không đúng");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setTokenVersion(user.getTokenVersion() + 1);
        return response(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse me(String username) {
        return response(userRepository.findByUsername(username).orElseThrow(() -> new BadCredentialsException("Không tìm thấy tài khoản")));
    }

    private AuthResponse response(User user) {
        UserDetails details = org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                .password(user.getPassword()).roles(user.getRole().name()).build();
        return AuthResponse.builder().userId(user.getId()).username(user.getUsername()).fullName(user.getFullName())
                .role(user.getRole()).subject(user.getSubject()).mustChangePassword(user.isMustChangePassword())
                .token(jwtService.generateToken(details, user.getId(), user.getRole().name(), user.getFullName(), user.getTokenVersion())).build();
    }

    public List<UserDto> getUsersByRole(Role role) {
        return (role == null ? userRepository.findAll() : userRepository.findByRole(role)).stream()
                .map(u -> UserDto.builder().id(u.getId()).username(u.getUsername()).fullName(u.getFullName())
                        .email(u.getEmail()).phone(u.getPhone()).role(u.getRole()).status(u.getStatus()).build()).toList();
    }

    // Existing student endpoints are outside the administration feature.
    public StudentDto createStudent(CreateStudentRequest request) { return StudentDto.builder().fullName(request.getFullName()).build(); }
    public List<StudentDto> getAllStudents(Long parentId) { return List.of(); }
    public StudentDto getStudentById(Long id) { return StudentDto.builder().id(id).build(); }
}
