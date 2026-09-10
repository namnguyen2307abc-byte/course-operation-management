package com.example.courseoperationmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.courseoperationmanagement.dto.ChangePasswordRequest;
import com.example.courseoperationmanagement.dto.RegisterRequest;
import com.example.courseoperationmanagement.entity.Role;
import com.example.courseoperationmanagement.entity.User;
import com.example.courseoperationmanagement.entity.UserStatus;
import com.example.courseoperationmanagement.exception.DuplicateEmailException;
import com.example.courseoperationmanagement.exception.InvalidCurrentPasswordException;
import com.example.courseoperationmanagement.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void registerCreatesActiveStudentWithHashedPassword() {
        RegisterRequest request = registerRequest("Linh Nguyen", "LINH@example.com", "password123");
        when(userRepository.existsByEmail("linh@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = userService.register(request);

        assertThat(created.getFullName()).isEqualTo("Linh Nguyen");
        assertThat(created.getEmail()).isEqualTo("linh@example.com");
        assertThat(created.getPassword()).isEqualTo("$2a$hash");
        assertThat(created.getRole()).isEqualTo(Role.STUDENT);
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = registerRequest("Linh Nguyen", "linh@example.com", "password123");
        when(userRepository.existsByEmail("linh@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void changePasswordVerifiesCurrentPasswordAndStoresHash() {
        User user = user("linh@example.com", "old-hash", Role.STUDENT, UserStatus.ACTIVE);
        ChangePasswordRequest request = changePasswordRequest("oldPassword123", "newPassword123");
        when(userRepository.findByEmail("linh@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPassword123", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("newPassword123")).thenReturn("new-hash");

        userService.changePassword("linh@example.com", request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("new-hash");
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        User user = user("linh@example.com", "old-hash", Role.STUDENT, UserStatus.ACTIVE);
        ChangePasswordRequest request = changePasswordRequest("wrongPassword", "newPassword123");
        when(userRepository.findByEmail("linh@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword("linh@example.com", request))
                .isInstanceOf(InvalidCurrentPasswordException.class);
    }

    private RegisterRequest registerRequest(String fullName, String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setFullName(fullName);
        request.setEmail(email);
        request.setPassword(password);
        request.setConfirmPassword(password);
        return request;
    }

    private ChangePasswordRequest changePasswordRequest(String currentPassword, String newPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(currentPassword);
        request.setNewPassword(newPassword);
        request.setConfirmPassword(newPassword);
        return request;
    }

    private User user(String email, String password, Role role, UserStatus status) {
        User user = new User();
        user.setFullName("Linh Nguyen");
        user.setEmail(email);
        user.setPassword(password);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
