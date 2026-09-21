package com.talent.management.features.attendance_makeup.repository;

import com.talent.management.shared.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AttendanceLessonRepository extends JpaRepository<Lesson, Long> {

    /**
     * Lấy danh sách các buổi học của học viên thông qua lớp học viên đã đăng ký
     */
    @Query("SELECT l FROM Lesson l " +
           "JOIN Enrollment e ON e.classEntity.id = l.classEntity.id " +
           "WHERE e.student.id = :studentId " +
           "ORDER BY l.lessonDate ASC, l.startTime ASC")
    List<Lesson> findLessonsByStudentId(@Param("studentId") Long studentId);

    /**
     * Lấy các ca học khả dụng cho việc xếp lịch học bù (diễn ra từ ngày hôm nay trở đi)
     */
    @Query("SELECT l FROM Lesson l " +
           "WHERE l.lessonDate >= :fromDate " +
           "ORDER BY l.lessonDate ASC, l.startTime ASC")
    List<Lesson> findAvailableUpcomingLessons(@Param("fromDate") LocalDate fromDate);

    /**
     * Lấy các ca học cùng khóa học để học viên học bù
     */
    @Query("SELECT l FROM Lesson l " +
           "WHERE l.classEntity.course.id = :courseId AND l.lessonDate >= :fromDate " +
           "ORDER BY l.lessonDate ASC, l.startTime ASC")
    List<Lesson> findUpcomingLessonsByCourseId(@Param("courseId") Long courseId, @Param("fromDate") LocalDate fromDate);

    /**
     * Lấy tên các lớp học mà học viên đang theo học
     */
    @Query("SELECT DISTINCT e.classEntity.className FROM Enrollment e WHERE e.student.id = :studentId")
    List<String> findClassNamesByStudentId(@Param("studentId") Long studentId);
}

