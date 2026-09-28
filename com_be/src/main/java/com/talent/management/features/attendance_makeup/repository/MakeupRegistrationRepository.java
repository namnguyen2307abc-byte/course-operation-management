package com.talent.management.features.attendance_makeup.repository;

import com.talent.management.shared.entity.MakeupRegistration;
import com.talent.management.shared.enums.MakeupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MakeupRegistrationRepository extends JpaRepository<MakeupRegistration, Long> {

    /** Tìm tất cả yêu cầu học bù sắp xếp giảm dần theo thời gian tạo */
    List<MakeupRegistration> findAllByOrderByCreatedAtDesc();

    /** Lọc yêu cầu học bù theo trạng thái (PENDING, SCHEDULED, COMPLETED, CANCELLED) */
    List<MakeupRegistration> findByStatusOrderByCreatedAtDesc(MakeupStatus status);

    /** Tìm yêu cầu học bù theo học viên */
    List<MakeupRegistration> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    /** Lọc yêu cầu học bù theo học viên và trạng thái */
    List<MakeupRegistration> findByStudentIdAndStatusOrderByCreatedAtDesc(Long studentId, MakeupStatus status);

    /** Tìm yêu cầu học bù theo đơn nghỉ liên kết (nếu cần truy xuất đơn lẻ) */
    Optional<MakeupRegistration> findByAbsenceRequestId(Long absenceRequestId);

    /**
     * BATCH FETCH — Fix N+1 query trong getAbsenceRequests().
     * Truy xuất toàn bộ MakeupRegistration theo danh sách absenceRequestId một lần.
     */
    List<MakeupRegistration> findByAbsenceRequestIdIn(List<Long> absenceRequestIds);

    /** Tìm yêu cầu học bù của các con thuộc phụ huynh */
    List<MakeupRegistration> findByStudentParentIdOrderByCreatedAtDesc(Long parentId);

    /** Lọc yêu cầu học bù của phụ huynh theo trạng thái */
    List<MakeupRegistration> findByStudentParentIdAndStatusOrderByCreatedAtDesc(Long parentId, MakeupStatus status);

    /**
     * Tìm học bù theo buổi học bù và học viên và trạng thái (hỗ trợ luồng 4 điểm danh tự động hoàn thành).
     * Ưu tiên findFirst để tránh duplicate nếu dữ liệu bị lỗi.
     */
    Optional<MakeupRegistration> findFirstByTargetLessonIdAndStudentIdAndStatus(
            Long targetLessonId, Long studentId, MakeupStatus status);

    /**
     * Tìm ca bù theo ID và trong tập trạng thái cho phép hủy (PENDING, SCHEDULED).
     * Dùng để validate trước khi cancel.
     */
    Optional<MakeupRegistration> findByIdAndStatusIn(Long id, List<MakeupStatus> statuses);
}
