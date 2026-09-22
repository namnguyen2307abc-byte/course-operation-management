package com.talent.management.features.course_enrollment.repository;

import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.enums.ClassStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClassRepository extends JpaRepository<ClassEntity, Long> {
    List<ClassEntity> findByCourseIdAndStatusOrderByStartDateAsc(Long courseId, ClassStatus status);
}
