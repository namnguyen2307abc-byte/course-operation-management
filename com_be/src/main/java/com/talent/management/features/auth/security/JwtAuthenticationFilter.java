package com.talent.management.features.auth.security;

import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.auth.service.JwtService;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) { chain.doFilter(request, response); return; }
        User authenticatedUser;
        try {
            String token = header.substring(7);
            String username = jwtService.extractUsername(token);
            authenticatedUser = userRepository.findByUsername(username).orElseThrow();
            UserDetails details = org.springframework.security.core.userdetails.User.withUsername(username)
                    .password(authenticatedUser.getPassword()).roles(authenticatedUser.getRole().name()).build();
            Number version = jwtService.extractClaim(token, claims -> claims.get("version", Number.class));
            if (authenticatedUser.getStatus() != UserStatus.ACTIVE || !jwtService.isTokenValid(token, details)
                    || version == null || version.longValue() != authenticatedUser.getTokenVersion()) {
                throw new IllegalArgumentException("Invalid session");
            }
            var authentication = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.\"}");
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (authenticatedUser.isMustChangePassword() && path.startsWith("/api/")
                && !path.equals("/api/auth/change-password") && !path.equals("/api/auth/me")) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"PASSWORD_CHANGE_REQUIRED\",\"message\":\"Vui lòng đổi mật khẩu trước khi tiếp tục.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
