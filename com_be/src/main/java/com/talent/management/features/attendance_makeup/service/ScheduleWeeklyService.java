package com.talent.management.features.attendance_makeup.service;

import com.talent.management.features.attendance_makeup.dto.request.BatchAttendanceSaveRequest;
import com.talent.management.features.attendance_makeup.dto.request.CreateClassWithLessonsRequest;
import com.talent.management.features.attendance_makeup.dto.response.LessonAttendanceSheetResponse;
import com.talent.management.features.attendance_makeup.dto.response.WeeklyScheduleResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ScheduleWeeklyService {

    /**
     * Lấy thời khóa biểu tuần theo ngày chỉ định và phân quyền người dùng
     */
    WeeklyScheduleResponse getWeeklySchedule(LocalDate date, Long studentId, Long teacherId, Long branchId);

    /**
     * Lấy danh sách điểm danh chi tiết của một buổi học (kèm kiểm tra quyền canEdit)
     */
    LessonAttendanceSheetResponse getLessonAttendanceSheet(Long lessonId);

    /**
     * Lưu danh sách điểm danh cả lớp (Batch Attendance)
     * Ràng buộc: Giáo viên chỉ được lưu trong ngày hôm đó (lessonDate == today).
     * Quản trị viên/Staff được sửa bất kỳ lúc nào.
     */
    void saveLessonAttendanceBatch(Long lessonId, BatchAttendanceSaveRequest request);

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
}
