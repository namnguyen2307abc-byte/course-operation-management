package com.talent.management.features.weekly_schedule.repository;

import com.talent.management.shared.entity.Lesson;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceLessonRepository extends JpaRepository<Lesson, Long> {

    /**
     * Buổi học của học viên thông qua lớp đang ghi danh chính thức (ENROLLED)
     */
    @Query("SELECT DISTINCT l FROM Lesson l " +
           "JOIN Enrollment e ON e.classEntity.id = l.classEntity.id " +
           "WHERE e.student.id = :studentId " +
           "AND e.status = com.talent.management.shared.enums.EnrollmentStatus.ENROLLED " +
           "ORDER BY l.lessonDate ASC, l.startTime ASC")
    List<Lesson> findLessonsByStudentId(@Param("studentId") Long studentId);

    /**
     * Tên các lớp học viên đang học (ENROLLED)
     */
    @Query("SELECT DISTINCT e.classEntity.className FROM Enrollment e " +
           "WHERE e.student.id = :studentId " +
           "AND e.status = com.talent.management.shared.enums.EnrollmentStatus.ENROLLED")
    List<String> findClassNamesByStudentId(@Param("studentId") Long studentId);

    /** Khóa buổi học khi xếp học bù để hai thao tác đồng thời không vượt sĩ số. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lesson l WHERE l.id = :id")
    Optional<Lesson> findByIdForUpdate(@Param("id") Long id);

    /**
     * Lấy toàn bộ ca học trong khoảng ngày (Tuần)
     */
    List<Lesson> findByLessonDateBetweenOrderByLessonDateAscStartTimeAsc(LocalDate startDate, LocalDate endDate);

    /**
     * Ca dạy của giáo viên trong tuần (fallback sang giáo viên của lớp nếu buổi chưa gán trực tiếp)
     */
    @Query("SELECT DISTINCT l FROM Lesson l LEFT JOIN l.classEntity c " +
           "WHERE (l.teacher.id = :teacherId OR (l.teacher IS NULL AND c.teacher.id = :teacherId)) " +
           "AND l.lessonDate BETWEEN :startDate AND :endDate " +
           "ORDER BY l.lessonDate ASC, l.startTime ASC")
    List<Lesson> findByTeacherIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(
            @Param("teacherId") Long teacherId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Lấy ca học của học viên trong khoảng ngày (Tuần)
     */
    @Query("SELECT DISTINCT l FROM Lesson l " +
           "JOIN Enrollment e ON e.classEntity.id = l.classEntity.id " +
           "WHERE e.student.id = :studentId " +
           "AND l.lessonDate BETWEEN :startDate AND :endDate " +
           "AND e.status = com.talent.management.shared.enums.EnrollmentStatus.ENROLLED " +
           "ORDER BY l.lessonDate ASC, l.startTime ASC")
    List<Lesson> findLessonsByStudentIdAndDateBetween(
            @Param("studentId") Long studentId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Lấy ca học theo lớp học trong khoảng ngày
     */
    List<Lesson> findByClassEntityIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(
            Long classId, LocalDate startDate, LocalDate endDate);

    /**
     * Lấy tất cả ca học của một lớp học sắp xếp theo số thứ tự buổi
     */
    List<Lesson> findByClassEntityIdOrderBySessionNumberAscLessonDateAscStartTimeAsc(Long classId);

    /**
     * Lấy ca học theo cơ sở trong khoảng ngày
     */
    List<Lesson> findByClassEntityBranchIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(
            Long branchId, LocalDate startDate, LocalDate endDate);

    /**
     * Tìm số thứ tự buổi học lớn nhất của một lớp
     */
    @Query("SELECT COALESCE(MAX(l.sessionNumber), 0) FROM Lesson l WHERE l.classEntity.id = :classId")
    int findMaxSessionNumberByClassId(@Param("classId") Long classId);

    /**
     * Ứng viên ca bù cùng trình độ: cùng chương trình + cùng level, cùng số thứ tự buổi,
     * diễn ra từ hôm nay, thuộc lớp khác lớp gốc.
     */
    @Query("SELECT l FROM Lesson l " +
           "WHERE l.lessonDate >= :fromDate " +
           "AND l.sessionNumber = :sessionNumber " +
           "AND l.classEntity.id <> :excludeClassId " +
           "AND l.status = com.talent.management.shared.enums.LessonStatus.SCHEDULED " +
           "AND l.classEntity.status IN (com.talent.management.shared.enums.ClassStatus.OPEN, com.talent.management.shared.enums.ClassStatus.IN_PROGRESS) " +
           "AND l.classEntity.course.program.id = :programId " +
           "AND l.classEntity.course.level = :level " +
           "ORDER BY l.lessonDate ASC, l.startTime ASC")
    List<Lesson> findMakeupCandidatesByLevel(@Param("fromDate") LocalDate fromDate,
                                             @Param("sessionNumber") Integer sessionNumber,
                                             @Param("excludeClassId") Long excludeClassId,
                                             @Param("programId") Long programId,
                                             @Param("level") String level);

    /**
     * Ứng viên ca bù khi khóa học không có thông tin level: cùng khóa học, cùng số thứ tự buổi.
     */
    @Query("SELECT l FROM Lesson l " +
           "WHERE l.lessonDate >= :fromDate " +
           "AND l.sessionNumber = :sessionNumber " +
           "AND l.classEntity.id <> :excludeClassId " +
           "AND l.status = com.talent.management.shared.enums.LessonStatus.SCHEDULED " +
           "AND l.classEntity.status IN (com.talent.management.shared.enums.ClassStatus.OPEN, com.talent.management.shared.enums.ClassStatus.IN_PROGRESS) " +
           "AND l.classEntity.course.id = :courseId " +
           "ORDER BY l.lessonDate ASC, l.startTime ASC")
    List<Lesson> findMakeupCandidatesByCourse(@Param("fromDate") LocalDate fromDate,
                                              @Param("sessionNumber") Integer sessionNumber,
                                              @Param("excludeClassId") Long excludeClassId,
                                              @Param("courseId") Long courseId);

    /**
     * Ứng viên điều phối ghép bù linh hoạt cùng trình độ khi các lớp song song đã dạy qua buổi gốc:
     * Tìm các buổi sắp diễn ra có sessionNumber lớn hơn buổi gốc (từ buổi nghỉ trở đi, không học lùi bài cũ).
     */
    @Query("SELECT l FROM Lesson l " +
           "WHERE l.lessonDate >= :fromDate " +
           "AND l.sessionNumber > :sessionNumber " +
           "AND l.classEntity.id <> :excludeClassId " +
           "AND l.status = com.talent.management.shared.enums.LessonStatus.SCHEDULED " +
           "AND l.classEntity.status IN (com.talent.management.shared.enums.ClassStatus.OPEN, com.talent.management.shared.enums.ClassStatus.IN_PROGRESS) " +
           "AND l.classEntity.course.program.id = :programId " +
           "AND l.classEntity.course.level = :level " +
           "ORDER BY l.lessonDate ASC, l.startTime ASC")
    List<Lesson> findFlexibleMakeupCandidatesByLevel(@Param("fromDate") LocalDate fromDate,
                                                     @Param("sessionNumber") Integer sessionNumber,
                                                     @Param("excludeClassId") Long excludeClassId,
                                                     @Param("programId") Long programId,
                                                     @Param("level") String level);

    /**
     * Ứng viên điều phối ghép bù linh hoạt cùng khóa học khi các lớp song song đã dạy qua buổi gốc.
     * Chỉ tìm các buổi từ bài nghỉ trở đi (sessionNumber > :sessionNumber).
     */
    @Query("SELECT l FROM Lesson l " +
           "WHERE l.lessonDate >= :fromDate " +
           "AND l.sessionNumber > :sessionNumber " +
           "AND l.classEntity.id <> :excludeClassId " +
           "AND l.status = com.talent.management.shared.enums.LessonStatus.SCHEDULED " +
           "AND l.classEntity.status IN (com.talent.management.shared.enums.ClassStatus.OPEN, com.talent.management.shared.enums.ClassStatus.IN_PROGRESS) " +
           "AND l.classEntity.course.id = :courseId " +
           "ORDER BY l.lessonDate ASC, l.startTime ASC")
    List<Lesson> findFlexibleMakeupCandidatesByCourse(@Param("fromDate") LocalDate fromDate,
                                                      @Param("sessionNumber") Integer sessionNumber,
                                                      @Param("excludeClassId") Long excludeClassId,
                                                      @Param("courseId") Long courseId);
}
