package com.talent.management.features.weekly_schedule.service;

import com.talent.management.features.weekly_schedule.dto.request.CreateClassWithLessonsRequest;
import com.talent.management.features.weekly_schedule.dto.response.WeeklyScheduleResponse;

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
}
