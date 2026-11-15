package com.talent.management.features.placement_test.repository;

import com.talent.management.features.placement_test.entity.PlacementSchedule;
import com.talent.management.features.placement_test.entity.PlacementScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlacementScheduleRepository extends JpaRepository<PlacementSchedule, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PlacementSchedule s where s.id = :id")
    Optional<PlacementSchedule> findByIdForUpdate(@Param("id") Long id);

    boolean existsByEnrollmentRequestId(Long enrollmentRequestId);

    boolean existsByEnrollmentRequestIdAndStatusNot(Long enrollmentRequestId, PlacementScheduleStatus status);

    Optional<PlacementSchedule> findFirstByEnrollmentRequestStudentIdOrderByTestDateDescIdDesc(Long studentId);

    Optional<PlacementSchedule> findFirstByEnrollmentRequestStudentIdAndProgramIdAndStatusAndRecommendedCourseProgramIdOrderByTestDateDescIdDesc(
            Long studentId, Long programId, PlacementScheduleStatus status, Long courseProgramId);

    Optional<PlacementSchedule> findFirstByEnrollmentRequestStudentIdAndStatusOrderByTestDateDescIdDesc(Long studentId, PlacementScheduleStatus status);

    List<PlacementSchedule> findAllByOrderByTestDateDesc();

    List<PlacementSchedule> findBySubjectIgnoreCaseOrderByTestDateDesc(String subject);

    List<PlacementSchedule> findByParentIdOrderByTestDateAsc(Long parentId);

    List<PlacementSchedule> findByParentEmailOrderByTestDateAsc(String parentEmail);

    List<PlacementSchedule> findBySubjectIgnoreCaseAndParentEmailOrderByTestDateAsc(String subject, String parentEmail);
}
