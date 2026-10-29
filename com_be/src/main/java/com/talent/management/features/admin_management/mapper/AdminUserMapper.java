package com.talent.management.features.admin_management.mapper;
import com.talent.management.shared.entity.User;
import com.talent.management.features.admin_management.dto.response.AdminUserResponse;
import org.springframework.stereotype.Component;
@Component
public class AdminUserMapper {
    public AdminUserResponse toResponse(User u) {
        return new AdminUserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getPhone(),
                u.getRole(), u.getStatus(), u.getSubject(), u.isMustChangePassword(), u.getCreatedAt(), u.getTeachingSubject());
    }
}
