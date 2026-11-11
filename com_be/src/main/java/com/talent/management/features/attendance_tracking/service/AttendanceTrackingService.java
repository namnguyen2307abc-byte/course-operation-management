package com.talent.management.features.attendance_tracking.service;

import com.talent.management.features.attendance_tracking.dto.request.BatchAttendanceSaveRequest;
import com.talent.management.features.attendance_tracking.dto.response.EnrolledClassOptionResponse;
import com.talent.management.features.attendance_tracking.dto.response.LessonAttendanceSheetResponse;
import com.talent.management.features.attendance_tracking.dto.response.StudentAttendanceTrackingResponse;

import java.util.List;
import java.util.Map;

public interface AttendanceTrackingService {

    /**
     * Lấy danh sách học viên được phép tra cứu theo phân quyền tài khoản hiện tại
     */
    List<Map<String, Object>> getTrackableStudents();

    /**
     * Lấy danh sách điểm danh chi tiết của một buổi học (kèm kiểm tra quyền canEdit)
     */
    LessonAttendanceSheetResponse getLessonAttendanceSheet(Long lessonId);

    /**
     * Lưu danh sách điểm danh cả lớp (Batch Attendance)
     */
    void saveLessonAttendanceBatch(Long lessonId, BatchAttendanceSaveRequest request);

    /**
     * Lấy danh sách lớp học mà học viên đã ghi danh
     */
    List<EnrolledClassOptionResponse> getEnrolledClassesForStudent(Long studentId);

    /**
     * Tra cứu chi tiết tiến độ điểm danh toàn bộ khóa học của học viên
     */
    StudentAttendanceTrackingResponse getStudentAttendanceTracking(Long studentId, Long classId);
}
