package com.talent.management.features.course_enrollment.repository;

import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.enums.ClassStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("courseEnrollmentClassRepository")
public interface ClassRepository extends JpaRepository<ClassEntity, Long> {
    List<ClassEntity> findByCourseIdAndStatusOrderByStartDateAsc(Long courseId, ClassStatus status);
}
