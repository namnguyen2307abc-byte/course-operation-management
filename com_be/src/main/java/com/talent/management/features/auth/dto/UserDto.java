package com.talent.management.features.auth.dto;

import com.talent.management.shared.enums.Role;
import com.talent.management.shared.enums.UserStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserDto {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private Role role;
    private UserStatus status;
}
