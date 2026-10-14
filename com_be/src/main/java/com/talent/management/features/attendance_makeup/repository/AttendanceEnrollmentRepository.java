package com.talent.management.features.attendance_makeup.repository;

import com.talent.management.shared.entity.Enrollment;
import com.talent.management.shared.enums.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AttendanceEnrollmentRepository extends JpaRepository<Enrollment, Long> {

    /**
     * Lấy danh sách học viên đã ghi danh vào lớp kèm JOIN FETCH student (chống N+1 query)
     */
    @Query("SELECT e FROM Enrollment e " +
           "JOIN FETCH e.student s " +
           "WHERE e.classEntity.id = :classId " +
           "AND e.status IN :statuses " +
           "ORDER BY s.fullName ASC")
    List<Enrollment> findEnrollmentsByClassIdAndStatusIn(
            @Param("classId") Long classId,
            @Param("statuses") Collection<EnrollmentStatus> statuses);

    default List<Enrollment> findActiveEnrollmentsByClassId(Long classId) {
        return findEnrollmentsByClassIdAndStatusIn(
                classId,
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.PENDING_PAYMENT)
        );
    }

    /**
     * ID các lớp mà học viên đang thuộc (dùng để loại trừ khỏi danh sách lớp học bù).
     */
    @Query("SELECT e.classEntity.id FROM Enrollment e " +
           "WHERE e.student.id = :studentId AND e.status IN :statuses")
    List<Long> findClassIdsByStudentIdAndStatusIn(
            @Param("studentId") Long studentId,
            @Param("statuses") Collection<EnrollmentStatus> statuses);
}
