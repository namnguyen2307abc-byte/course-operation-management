package com.talent.management.features.admin_management.service;
import java.time.LocalDate;
import com.talent.management.features.admin_management.dto.response.AdminDashboardResponse;
public interface AdminDashboardService { AdminDashboardResponse overview(LocalDate from, LocalDate to); }
