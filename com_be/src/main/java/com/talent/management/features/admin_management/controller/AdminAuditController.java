package com.talent.management.features.admin_management.controller;
import com.talent.management.features.admin_management.service.AdminAuditService;
import com.talent.management.features.admin_management.dto.response.AdminAuditResponse;
import com.talent.management.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
@RestController @RequestMapping("/api/admin/audit-logs") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {
    private final AdminAuditService service;
    @GetMapping public PageResponse<AdminAuditResponse> search(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action, @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size) {
        return service.search(keyword, action, targetId, from, to, page, size);
    }
}
