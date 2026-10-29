package com.talent.management.features.branch_facility_enrollment.repository;

import com.talent.management.shared.entity.Lesson;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface FacilityLessonRepository extends JpaRepository<Lesson, Long> {
    @EntityGraph(attributePaths = {"classEntity", "classEntity.course", "teacher"})
    @Query("""
            select l from Lesson l left join l.room r left join l.classEntity.room defaultRoom
            where (r.id = :roomId or (l.room is null and defaultRoom.id = :roomId))
              and l.status = com.talent.management.shared.enums.LessonStatus.SCHEDULED
              and (l.lessonDate > :today or (l.lessonDate = :today and l.endTime > cast(:now as LocalTime)))
            order by l.lessonDate, l.startTime, l.id
            """)
    List<Lesson> findRemainingRoomLessons(@Param("roomId") Long roomId,
                                           @Param("today") LocalDate today, @Param("now") LocalTime now);
}
