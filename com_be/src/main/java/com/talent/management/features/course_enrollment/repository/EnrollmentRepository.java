package com.talent.management.features.course_enrollment.repository;

import com.talent.management.shared.entity.Enrollment;
import com.talent.management.shared.enums.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository("courseEnrollmentRepository")
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    boolean existsByStudentIdAndClassEntityIdAndStatusIn(
            Long studentId,
            Long classId,
            Collection<EnrollmentStatus> statuses
    );
}
