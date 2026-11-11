package com.talent.management.features.placement_test.repository;

import com.talent.management.features.placement_test.entity.PlacementSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlacementScheduleRepository extends JpaRepository<PlacementSchedule, Long> {

    boolean existsByEnrollmentRequestId(Long enrollmentRequestId);

    Optional<PlacementSchedule> findFirstByEnrollmentRequestStudentIdOrderByTestDateDescIdDesc(Long studentId);

    List<PlacementSchedule> findAllByOrderByTestDateDesc();

    List<PlacementSchedule> findBySubjectIgnoreCaseOrderByTestDateDesc(String subject);

    List<PlacementSchedule> findByParentIdOrderByTestDateAsc(Long parentId);

    List<PlacementSchedule> findByParentEmailOrderByTestDateAsc(String parentEmail);

    List<PlacementSchedule> findBySubjectIgnoreCaseAndParentEmailOrderByTestDateAsc(String subject, String parentEmail);
}
