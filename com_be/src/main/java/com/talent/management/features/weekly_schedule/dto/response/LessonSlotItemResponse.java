package com.talent.management.features.weekly_schedule.dto.response;

import com.talent.management.features.attendance_tracking.dto.response.StudentLessonAttendanceResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonSlotItemResponse {
    private Long lessonId;
    private Long classId;
    private String classCode;
    private String className;
    private String courseName;
    private String roomName;
    private String branchName;
    private Long teacherId;
    private String teacherName;
    private String teacherUsername;
    private LocalDate lessonDate;
    private String dayOfWeek;
    private int dayOfWeekValue; // 1 = Thứ 2, 7 = Chủ nhật
    private int slotNumber;
    private String timeRange;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer sessionNumber;
    private String status;
    private boolean isToday;
    private boolean isPast;
    private boolean canEdit;

    // Màu sắc đồng bộ theo slot từ database / hệ thống
    private String slotColor;
    private String slotBgColor;
    private String slotBorderColor;

    // Trạng thái hiển thị theo vai trò
    private String attendanceStatus; // PRESENT, ABSENT_UNEXCUSED, ABSENT_EXCUSED, NOT_MARKED
    private List<StudentLessonAttendanceResponse> studentAttendances;
    private Boolean attendanceCompleted; // Dành cho GV: true nếu lớp đã được điểm danh
    private int totalStudents;
    private int presentCount;
    private int absentCount;
}
