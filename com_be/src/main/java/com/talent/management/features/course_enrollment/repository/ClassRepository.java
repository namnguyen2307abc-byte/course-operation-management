package com.talent.management.features.course_enrollment.repository;

import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.enums.ClassStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("courseEnrollmentClassRepository")
public interface ClassRepository extends JpaRepository<ClassEntity, Long> {
    List<ClassEntity> findByCourseIdAndStatusOrderByStartDateAsc(Long courseId, ClassStatus status);

    List<ClassEntity> findByStatusOrderByStartDateAsc(ClassStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClassEntity c where c.id = :id")
    Optional<ClassEntity> findByIdForUpdate(@Param("id") Long id);
}
