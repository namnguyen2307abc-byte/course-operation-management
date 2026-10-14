package com.talent.management.features.course_management.repository;

import com.talent.management.shared.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseManagementRepository extends JpaRepository<Course, Long> {

    List<Course> findAllByOrderByProgramNameAscNameAsc();

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    @Query("SELECT c FROM Course c JOIN FETCH c.program ORDER BY c.program.name ASC, c.name ASC")
    List<Course> findAllWithProgram();

    @Query("SELECT COUNT(cl) FROM ClassEntity cl WHERE cl.course.id = :courseId")
    Long countClassesByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.classEntity.course.id = :courseId")
    Long countEnrollmentsByCourseId(@Param("courseId") Long courseId);

    Optional<Course> findByCode(String code);
}
