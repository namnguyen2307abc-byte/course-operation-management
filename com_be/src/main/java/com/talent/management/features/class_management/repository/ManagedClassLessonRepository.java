package com.talent.management.features.class_management.repository;

import com.talent.management.shared.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedClassLessonRepository extends JpaRepository<Lesson, Long> {
    boolean existsByClassEntityId(Long classId);
}
