package com.talent.management.features.attendance_makeup.repository;

import com.talent.management.shared.entity.MakeupRegistration;
import com.talent.management.shared.enums.MakeupStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository truy vấn & khóa ghi (PESSIMISTIC_WRITE) cho ca học bù (makeup_registrations).
 * Trọng tâm bảo vệ quy tắc 3 lượt/khóa: nhóm hàm {@code countQuotaByStudent*}
 * đếm cả trạng thái hoạt động lẫn ca CANCELLED có tag "- KHÓA]".
 */
@Repository
public interface MakeupRegistrationRepository extends JpaRepository<MakeupRegistration, Long> {

    /** Khóa ca bù khi xếp, hủy hoặc hoàn thành để các thao tác đồng thời không ghi chồng nhau. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT mr FROM MakeupRegistration mr WHERE mr.id = :id")
    Optional<MakeupRegistration> findByIdForUpdate(@Param("id") Long id);

    /** Ca bù có buổi gốc hoặc buổi bù do giáo viên này phụ trách (buổi chưa gán giáo viên lấy giáo viên của lớp) */
    @Query("SELECT DISTINCT mr FROM MakeupRegistration mr " +
           "JOIN mr.originalLesson ol LEFT JOIN ol.classEntity oc " +
           "LEFT JOIN mr.targetLesson tl LEFT JOIN tl.classEntity tc " +
           "WHERE (ol.teacher.id = :teacherId OR (ol.teacher IS NULL AND oc.teacher.id = :teacherId) " +
           "OR tl.teacher.id = :teacherId OR (tl.id IS NOT NULL AND tl.teacher IS NULL AND tc.teacher.id = :teacherId)) " +
           "ORDER BY mr.createdAt DESC")
    List<MakeupRegistration> findByTeacherId(@Param("teacherId") Long teacherId);

    @Query("SELECT DISTINCT mr FROM MakeupRegistration mr " +
           "JOIN mr.originalLesson ol LEFT JOIN ol.classEntity oc " +
           "LEFT JOIN mr.targetLesson tl LEFT JOIN tl.classEntity tc " +
           "WHERE mr.status = :status " +
           "AND (ol.teacher.id = :teacherId OR (ol.teacher IS NULL AND oc.teacher.id = :teacherId) " +
           "OR tl.teacher.id = :teacherId OR (tl.id IS NOT NULL AND tl.teacher IS NULL AND tc.teacher.id = :teacherId)) " +
           "ORDER BY mr.createdAt DESC")
    List<MakeupRegistration> findByTeacherIdAndStatus(@Param("teacherId") Long teacherId,
                                                      @Param("status") MakeupStatus status);

    /** Tìm tất cả yêu cầu học bù sắp xếp giảm dần theo thời gian tạo */
    List<MakeupRegistration> findAllByOrderByCreatedAtDesc();

    /** Lọc yêu cầu học bù theo trạng thái (PENDING, SCHEDULED, COMPLETED, MISSED, CANCELLED) */
    List<MakeupRegistration> findByStatusOrderByCreatedAtDesc(MakeupStatus status);

    /** Tìm yêu cầu học bù theo học viên */
    List<MakeupRegistration> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    /** Tìm yêu cầu học bù theo đơn nghỉ liên kết (nếu cần truy xuất đơn lẻ) */
    Optional<MakeupRegistration> findFirstByAbsenceRequestIdOrderByIdDesc(Long absenceRequestId);

    default Optional<MakeupRegistration> findByAbsenceRequestId(Long absenceRequestId) {
        return findFirstByAbsenceRequestIdOrderByIdDesc(absenceRequestId);
    }

    /**
     * BATCH FETCH — Fix N+1 query trong getAbsenceRequests().
     * Truy xuất toàn bộ MakeupRegistration theo danh sách absenceRequestId một lần.
     */
    List<MakeupRegistration> findByAbsenceRequestIdIn(List<Long> absenceRequestIds);

    /** Tìm yêu cầu học bù của các con thuộc phụ huynh */
    List<MakeupRegistration> findByStudentParentIdOrderByCreatedAtDesc(Long parentId);

    /** Lọc yêu cầu học bù của phụ huynh theo trạng thái */
    List<MakeupRegistration> findByStudentParentIdAndStatusOrderByCreatedAtDesc(Long parentId, MakeupStatus status);

    Optional<MakeupRegistration> findFirstByTargetLessonIdAndStudentIdAndStatusIn(
            Long targetLessonId, Long studentId, Collection<MakeupStatus> statuses);

    /** Học viên bù được xếp vào buổi học này (gồm cả ca đã hoàn thành/bỏ lỡ để không biến mất khỏi bảng điểm danh) */
    List<MakeupRegistration> findByTargetLessonIdAndStatusIn(Long targetLessonId, Collection<MakeupStatus> statuses);

    /** Học viên đã được chuyển khỏi buổi học gốc sang ca bù */
    List<MakeupRegistration> findByOriginalLessonIdAndStatusIn(Long originalLessonId, Collection<MakeupStatus> statuses);

    /** BATCH — ca bù đến (target) của nhiều buổi học cùng lúc */
    List<MakeupRegistration> findByTargetLessonIdInAndStatusIn(Collection<Long> targetLessonIds, Collection<MakeupStatus> statuses);

    /** BATCH — ca bù đi (original) của nhiều buổi học cùng lúc */
    List<MakeupRegistration> findByOriginalLessonIdInAndStatusIn(Collection<Long> originalLessonIds, Collection<MakeupStatus> statuses);

    /** Các ca bù của một học viên theo trạng thái — dùng để đồng bộ thời khóa biểu học viên */
    List<MakeupRegistration> findByStudentIdAndStatusIn(Long studentId, Collection<MakeupStatus> statuses);

    /** Đếm số học viên bù đang chiếm chỗ trong một buổi học */
    long countByTargetLessonIdAndStatusIn(Long targetLessonId, Collection<MakeupStatus> statuses);

    /** Đếm số ca học bù đang giữ/tiêu tốn lượt trong lớp (gồm cả ca bị khóa do vắng buổi bù). */
    @Query("SELECT COUNT(mr) FROM MakeupRegistration mr " +
           "WHERE mr.student.id = :studentId " +
           "AND mr.originalLesson.classEntity.id = :classId " +
           "AND (mr.status IN :statuses OR (mr.status = com.talent.management.shared.enums.MakeupStatus.CANCELLED AND mr.note LIKE :lockPattern))")
    long countQuotaByStudentAndClass(
            @Param("studentId") Long studentId,
            @Param("classId") Long classId,
            @Param("statuses") Collection<MakeupStatus> statuses,
            @Param("lockPattern") String lockPattern);

    /** Đếm số ca học bù đang giữ/tiêu tốn lượt trong khóa học (gồm cả ca bị khóa do vắng buổi bù). */
    @Query("SELECT COUNT(mr) FROM MakeupRegistration mr " +
           "WHERE mr.student.id = :studentId " +
           "AND mr.originalLesson.classEntity.course.id = :courseId " +
           "AND (mr.status IN :statuses OR (mr.status = com.talent.management.shared.enums.MakeupStatus.CANCELLED AND mr.note LIKE :lockPattern))")
    long countQuotaByStudentAndCourse(
            @Param("studentId") Long studentId,
            @Param("courseId") Long courseId,
            @Param("statuses") Collection<MakeupStatus> statuses,
            @Param("lockPattern") String lockPattern);

    /** Đếm tổng số ca học bù đang giữ/tiêu tốn lượt của học viên. */
    @Query("SELECT COUNT(mr) FROM MakeupRegistration mr " +
           "WHERE mr.student.id = :studentId " +
           "AND (mr.status IN :statuses OR (mr.status = com.talent.management.shared.enums.MakeupStatus.CANCELLED AND mr.note LIKE :lockPattern))")
    long countQuotaByStudent(
            @Param("studentId") Long studentId,
            @Param("statuses") Collection<MakeupStatus> statuses,
            @Param("lockPattern") String lockPattern);
}
