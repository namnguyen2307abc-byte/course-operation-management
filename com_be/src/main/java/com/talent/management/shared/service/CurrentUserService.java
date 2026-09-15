package com.talent.management.shared.service;

import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException("Chưa đăng nhập hoặc phiên đăng nhập không hợp lệ");
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng: " + username));
    }
}
