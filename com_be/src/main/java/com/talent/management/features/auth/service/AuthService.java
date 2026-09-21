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
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (user.getStatus() != UserStatus.ACTIVE
                || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities("ROLE_" + user.getRole().name())
                .build();
        String token = jwtService.generateToken(
                userDetails,
                user.getId(),
                user.getRole().name(),
                user.getFullName()
        );

        return AuthResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .token(token)
                .build();
    }

    public AuthResponse register(RegisterRequest request) {
        return AuthResponse.builder()
                .username(request.getUsername())
                .fullName(request.getFullName())
                .role(request.getRole() != null ? request.getRole() : Role.PARENT)
                .token("AUTH_TOKEN_" + request.getUsername())
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
