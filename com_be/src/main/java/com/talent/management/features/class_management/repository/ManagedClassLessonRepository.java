package com.talent.management.features.class_management.repository;

import com.talent.management.shared.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDate;

public interface ManagedClassLessonRepository extends JpaRepository<Lesson, Long> {
    boolean existsByClassEntityId(Long classId);
    int countByClassEntityId(Long classId);
    List<Lesson> findByClassEntityId(Long classId);
    @Query("""
            select l from Lesson l left join l.teacher t left join l.classEntity.teacher defaultTeacher
            where (t.id = :teacherId or (l.teacher is null and defaultTeacher.id = :teacherId))
              and l.lessonDate between :fromDate and :toDate
            """)
    List<Lesson> findByTeacherIdAndLessonDateBetween(@Param("teacherId") Long teacherId,
                                                     @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query("""
            select l from Lesson l left join l.room r left join l.classEntity.room defaultRoom
            where (r.id = :roomId or (l.room is null and defaultRoom.id = :roomId))
              and l.lessonDate between :fromDate and :toDate
            """)
    List<Lesson> findByRoomIdAndLessonDateBetween(@Param("roomId") Long roomId,
                                                  @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}

