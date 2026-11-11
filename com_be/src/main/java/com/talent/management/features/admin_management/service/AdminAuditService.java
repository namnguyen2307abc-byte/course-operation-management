package com.talent.management.features.admin_management.service;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.dto.PageResponse;
import com.talent.management.features.admin_management.dto.response.AdminAuditResponse;
import java.time.LocalDate;
public interface AdminAuditService {
    void record(User actor, User target, String action, String details);
    PageResponse<AdminAuditResponse> search(String keyword, String action, Long targetId, LocalDate from, LocalDate to, int page, int size);
}
