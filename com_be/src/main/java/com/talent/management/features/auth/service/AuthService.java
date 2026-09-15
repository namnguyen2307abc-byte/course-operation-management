package com.talent.management.features.auth.service;

import com.talent.management.features.auth.dto.*;
import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    public AuthResponse login(LoginRequest request) {
        Optional<User> userOpt = userRepository.findByUsername(request.getUsername());
        String fullName = userOpt.map(User::getFullName).orElse("Quản Trị Viên");
        Role role = userOpt.map(User::getRole).orElse(Role.ADMIN);

        return AuthResponse.builder()
                .username(request.getUsername())
                .fullName(fullName)
                .role(role)
                .token("AUTH_TOKEN_" + request.getUsername())
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
