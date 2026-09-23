package com.talent.management.features.tuition_payment.repository;

import com.talent.management.shared.entity.Enrollment;
import com.talent.management.shared.enums.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findByStudentId(Long studentId);

    List<Enrollment> findByClassEntityId(Long classId);

    List<Enrollment> findByStatus(EnrollmentStatus status);

    Optional<Enrollment> findByStudentIdAndClassEntityId(Long studentId, Long classId);

    boolean existsByStudentIdAndClassEntityIdAndStatusIn(Long studentId, Long classId, List<EnrollmentStatus> statuses);
}
