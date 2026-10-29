package com.talent.management.features.admin_management.dto.response;
import java.util.List;
public record AdminUserDetailResponse(AdminUserResponse user, List<LinkedRecord> classes,
        List<LinkedRecord> children, List<LinkedRecord> studentProfiles) {
    public record LinkedRecord(Long id, String name) {}
}
