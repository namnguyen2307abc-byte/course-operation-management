package com.example.courseoperationmanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.courseoperationmanagement.entity.Role;
import com.example.courseoperationmanagement.entity.User;
import com.example.courseoperationmanagement.entity.UserStatus;
import com.example.courseoperationmanagement.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @Test
    void loadActiveUserReturnsEnabledUserDetails() {
        when(userRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(user(Role.ADMIN, UserStatus.ACTIVE)));

        UserDetails userDetails = userDetailsService.loadUserByUsername("ADMIN@example.com");

        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.getUsername()).isEqualTo("admin@example.com");
        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void loadInactiveUserReturnsDisabledUserDetails() {
        when(userRepository.findByEmail("student@example.com"))
                .thenReturn(Optional.of(user(Role.STUDENT, UserStatus.INACTIVE)));

        UserDetails userDetails = userDetailsService.loadUserByUsername("student@example.com");

        assertThat(userDetails.isEnabled()).isFalse();
    }

    @Test
    void loadMissingUserThrowsUsernameNotFound() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    private User user(Role role, UserStatus status) {
        User user = new User();
        user.setFullName("Test User");
        user.setEmail(role == Role.ADMIN ? "admin@example.com" : "student@example.com");
        user.setPassword("$2a$hash");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
