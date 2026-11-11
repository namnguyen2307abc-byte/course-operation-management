package com.talent.management.features.admin_management.service;
import com.talent.management.features.admin_management.dto.request.*;
import com.talent.management.features.admin_management.dto.response.*;
import com.talent.management.shared.dto.PageResponse;
import com.talent.management.shared.enums.*;
import java.util.List;
public interface AdminUserService {
    PageResponse<AdminUserResponse> search(String keyword, Role role, UserStatus status, int page, int size);
    AdminUserDetailResponse detail(Long id);
    List<AdminUserDetailResponse.LinkedRecord> studentOptions(String keyword);
    AdminUserResponse create(CreateAdminUserRequest request);
    AdminUserResponse update(Long id, UpdateAdminUserRequest request);
    AdminUserResponse changeRole(Long id, ChangeUserRoleRequest request);
    AdminUserResponse changeStatus(Long id, ChangeUserStatusRequest request);
    AdminUserResponse resetPassword(Long id, ResetUserPasswordRequest request);
}
