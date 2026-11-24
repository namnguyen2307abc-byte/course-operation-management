package com.talent.management.features.weekly_schedule.repository;

import com.talent.management.shared.entity.ClassEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttendanceClassRepository extends JpaRepository<ClassEntity, Long> {
    Optional<ClassEntity> findByClassCode(String classCode);
    boolean existsByClassCode(String classCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ClassEntity c WHERE c.id = :id")
    Optional<ClassEntity> findByIdForUpdate(@Param("id") Long id);
}
