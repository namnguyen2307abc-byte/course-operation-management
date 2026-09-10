package com.example.courseoperationmanagement.service;

import com.example.courseoperationmanagement.dto.ChangePasswordRequest;
import com.example.courseoperationmanagement.dto.RegisterRequest;
import com.example.courseoperationmanagement.entity.Role;
import com.example.courseoperationmanagement.entity.User;
import com.example.courseoperationmanagement.entity.UserStatus;
import com.example.courseoperationmanagement.exception.DuplicateEmailException;
import com.example.courseoperationmanagement.exception.InvalidCurrentPasswordException;
import com.example.courseoperationmanagement.exception.UserNotFoundException;
import com.example.courseoperationmanagement.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("Email is already registered.");
        }

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new UserNotFoundException("User was not found."));
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User was not found."));
    }

    @Transactional(readOnly = true)
    public Page<User> searchUsers(String keyword, Role role, UserStatus status, Pageable pageable) {
        return userRepository.searchUsers(cleanKeyword(keyword), role, status, pageable);
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = findByEmail(email);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidCurrentPasswordException("Current password is incorrect.");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void activate(Long id) {
        User user = findById(id);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        log.info("Activated user account with id {}", id);
    }

    @Transactional
    public void deactivate(Long id) {
        User user = findById(id);
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
        log.info("Deactivated user account with id {}", id);
    }

    @Transactional
    public void changeRole(Long id, Role role) {
        User user = findById(id);
        Role previousRole = user.getRole();
        user.setRole(role);
        userRepository.save(user);
        log.info("Changed user {} role from {} to {}", id, previousRole, role);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String cleanKeyword(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }
}
