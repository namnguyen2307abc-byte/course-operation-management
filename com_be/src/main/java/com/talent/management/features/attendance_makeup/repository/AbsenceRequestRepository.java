package com.talent.management.features.attendance_makeup.repository;

import com.talent.management.shared.entity.AbsenceRequest;
import com.talent.management.shared.enums.AbsenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AbsenceRequestRepository extends JpaRepository<AbsenceRequest, Long> {

    /**
     * Tìm tất cả yêu cầu nghỉ học sắp xếp giảm dần theo thời gian tạo
     */
    List<AbsenceRequest> findAllByOrderByCreatedAtDesc();

    /**
     * Lọc yêu cầu nghỉ theo trạng thái
     */
    List<AbsenceRequest> findByStatusOrderByCreatedAtDesc(AbsenceStatus status);

    /**
     * Tìm yêu cầu nghỉ theo học viên
     */
    List<AbsenceRequest> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    /**
     * Lọc theo học viên và trạng thái
     */
    List<AbsenceRequest> findByStudentIdAndStatusOrderByCreatedAtDesc(Long studentId, AbsenceStatus status);

    /**
     * Tìm yêu cầu nghỉ cho các con của phụ huynh
     */
    List<AbsenceRequest> findByStudentParentIdOrderByCreatedAtDesc(Long parentId);

    /**
     * Lọc yêu cầu nghỉ của phụ huynh theo trạng thái
     */
    List<AbsenceRequest> findByStudentParentIdAndStatusOrderByCreatedAtDesc(Long parentId, AbsenceStatus status);

    /**
     * Tìm yêu cầu nghỉ thuộc các lớp/buổi do giáo viên phụ trách
     */
    List<AbsenceRequest> findByLessonTeacherIdOrderByCreatedAtDesc(Long teacherId);

    /**
     * Lọc yêu cầu nghỉ của giáo viên theo trạng thái
     */
    List<AbsenceRequest> findByLessonTeacherIdAndStatusOrderByCreatedAtDesc(Long teacherId, AbsenceStatus status);

    /**
     * Kiểm tra xem học viên đã có yêu cầu nghỉ cho buổi học này chưa
     */
    Optional<AbsenceRequest> findByStudentIdAndLessonId(Long studentId, Long lessonId);
}
