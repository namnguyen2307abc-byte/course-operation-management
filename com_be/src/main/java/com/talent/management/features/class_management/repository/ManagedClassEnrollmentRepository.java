package com.talent.management.features.class_management.repository;

import com.talent.management.shared.entity.Enrollment;
import com.talent.management.shared.enums.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedClassEnrollmentRepository extends JpaRepository<Enrollment, Long> {
    boolean existsByClassEntityId(Long classId);

    long countByClassEntityIdAndStatus(Long classId, EnrollmentStatus status);
}
