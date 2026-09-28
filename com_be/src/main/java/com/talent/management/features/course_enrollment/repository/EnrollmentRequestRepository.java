package com.talent.management.features.course_enrollment.repository;

import com.talent.management.features.course_enrollment.entity.EnrollmentRequest;
import com.talent.management.features.course_enrollment.enums.EnrollmentRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnrollmentRequestRepository extends JpaRepository<EnrollmentRequest, Long> {
    boolean existsByStudentIdAndClassEntityIdAndStatus(
            Long studentId,
            Long classId,
            EnrollmentRequestStatus status
    );

    List<EnrollmentRequest> findByRequestedByIdOrderByCreatedAtDesc(Long requestedById);

    List<EnrollmentRequest> findByStatusOrderByCreatedAtAsc(EnrollmentRequestStatus status);
}
