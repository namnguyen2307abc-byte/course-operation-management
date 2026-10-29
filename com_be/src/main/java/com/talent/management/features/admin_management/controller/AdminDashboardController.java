package com.talent.management.features.admin_management.controller;
import com.talent.management.features.admin_management.service.AdminDashboardService;
import com.talent.management.features.admin_management.dto.response.AdminDashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
@RestController @RequestMapping("/api/admin/dashboard") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {
    private final AdminDashboardService service;
    @GetMapping public AdminDashboardResponse overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) { return service.overview(from, to); }
}
