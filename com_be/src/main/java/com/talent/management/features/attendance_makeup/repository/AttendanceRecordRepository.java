package com.talent.management.features.attendance_makeup.repository;

import com.talent.management.shared.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<Attendance, Long> {

    /**
     * Tìm bản ghi điểm danh theo buổi học và học viên
     */
    Optional<Attendance> findByLessonIdAndStudentId(Long lessonId, Long studentId);
}
