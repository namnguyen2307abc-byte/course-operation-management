package com.talent.management.features.course_enrollment.repository;

import com.talent.management.shared.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findAllByOrderByProgramNameAscNameAsc();
}
