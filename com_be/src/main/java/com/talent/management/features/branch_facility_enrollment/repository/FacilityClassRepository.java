package com.talent.management.features.branch_facility_enrollment.repository;

import com.talent.management.shared.entity.ClassEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface FacilityClassRepository extends JpaRepository<ClassEntity, Long> {
    @EntityGraph(attributePaths = {"course", "course.program"})
    @Query("""
            select c from ClassEntity c left join c.room r
            where (c.status <> com.talent.management.shared.enums.ClassStatus.FINISHED and r.id = :roomId)
               or exists (select l.id from Lesson l left join l.room lessonRoom
                   where l.classEntity = c and (lessonRoom.id = :roomId or (l.room is null and r.id = :roomId))
                   and l.status = com.talent.management.shared.enums.LessonStatus.SCHEDULED
                   and (l.lessonDate > :today or (l.lessonDate = :today and l.endTime > cast(:now as LocalTime))))
            order by c.classCode
            """)
    List<ClassEntity> findClassesUsingRoom(@Param("roomId") Long roomId,
                                            @Param("today") LocalDate today, @Param("now") LocalTime now);
}
