package com.talent.management.features.admin_management.controller;
import com.talent.management.features.admin_management.dto.request.*;
import com.talent.management.features.admin_management.dto.response.*;
import com.talent.management.features.admin_management.service.AdminUserService;
import com.talent.management.shared.dto.PageResponse;
import com.talent.management.shared.enums.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/admin/users") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminUserService service;
    @GetMapping
    public PageResponse<AdminUserResponse> search(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Role role, @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size) {
        return service.search(keyword, role, status, page, size);
    }
    @GetMapping("/student-options")
    public List<AdminUserDetailResponse.LinkedRecord> studentOptions(@RequestParam(required = false) String keyword) { return service.studentOptions(keyword); }
    @GetMapping("/{id}") public AdminUserDetailResponse detail(@PathVariable Long id) { return service.detail(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse create(@Valid @RequestBody CreateAdminUserRequest request) { return service.create(request); }
    @PutMapping("/{id}") public AdminUserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateAdminUserRequest request) { return service.update(id, request); }
    @PatchMapping("/{id}/role") public AdminUserResponse role(@PathVariable Long id, @Valid @RequestBody ChangeUserRoleRequest request) { return service.changeRole(id, request); }
    @PatchMapping("/{id}/status") public AdminUserResponse status(@PathVariable Long id, @Valid @RequestBody ChangeUserStatusRequest request) { return service.changeStatus(id, request); }
    @PostMapping("/{id}/reset-password") public AdminUserResponse password(@PathVariable Long id, @Valid @RequestBody ResetUserPasswordRequest request) { return service.resetPassword(id, request); }
}
