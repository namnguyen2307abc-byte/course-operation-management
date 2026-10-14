package com.talent.management.features.course_enrollment.repository;

import com.talent.management.shared.entity.EnrollmentRequest;
import com.talent.management.shared.enums.EnrollmentRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRequestRepository extends JpaRepository<EnrollmentRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from EnrollmentRequest r where r.id = :id")
    Optional<EnrollmentRequest> findByIdForUpdate(@Param("id") Long id);

    boolean existsByStudentIdAndClassEntityIdAndStatus(
            Long studentId,
            Long classId,
            EnrollmentRequestStatus status
    );

    Optional<EnrollmentRequest> findByEnrollmentId(Long enrollmentId);

    List<EnrollmentRequest> findByRequestedByIdOrderByCreatedAtDesc(Long requestedById);

    List<EnrollmentRequest> findByStatusOrderByCreatedAtAsc(EnrollmentRequestStatus status);

    List<EnrollmentRequest> findByStatusInOrderByCreatedAtAsc(Collection<EnrollmentRequestStatus> statuses);

    boolean existsByStudentIdAndStatusIn(
            Long studentId,
            Collection<EnrollmentRequestStatus> statuses
    );
}
