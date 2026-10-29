package com.talent.management.features.admin_management.repository;
import com.talent.management.shared.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.*;
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long>, JpaSpecificationExecutor<AdminAuditLog> {}
