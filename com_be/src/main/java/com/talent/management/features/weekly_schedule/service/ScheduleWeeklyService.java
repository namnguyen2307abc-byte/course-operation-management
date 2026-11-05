package com.talent.management.features.weekly_schedule.service;

import com.talent.management.features.weekly_schedule.dto.request.CreateClassWithLessonsRequest;
import com.talent.management.features.weekly_schedule.dto.response.WeeklyScheduleResponse;
import com.talent.management.features.attendance_tracking.dto.request.BatchAttendanceSaveRequest;
import com.talent.management.features.attendance_tracking.dto.response.EnrolledClassOptionResponse;
import com.talent.management.features.attendance_tracking.dto.response.LessonAttendanceSheetResponse;
import com.talent.management.features.attendance_tracking.dto.response.StudentAttendanceTrackingResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ScheduleWeeklyService {

    /**
     * Lấy thời khóa biểu tuần theo ngày chỉ định và phân quyền người dùng
     */
    WeeklyScheduleResponse getWeeklySchedule(LocalDate date, Long studentId, Long teacherId, Long branchId);

    /**
     * Tạo lớp học mới và tự động sinh danh sách buổi học (lessons) vào thời khóa biểu
     */
    Long createClassWithLessons(CreateClassWithLessonsRequest request);

    /**
     * Lấy danh sách giáo viên để làm bộ lọc
     */
    List<Map<String, Object>> getFilterTeachers();

    /**
     * Lấy danh sách lớp học để làm bộ lọc
     */
    List<Map<String, Object>> getFilterClasses();

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
