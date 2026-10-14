package com.talent.management.features.attendance_makeup.controller;

import com.talent.management.features.attendance_makeup.dto.request.BatchAttendanceSaveRequest;
import com.talent.management.features.attendance_makeup.dto.request.CreateClassWithLessonsRequest;
import com.talent.management.features.attendance_makeup.dto.response.LessonAttendanceSheetResponse;
import com.talent.management.features.attendance_makeup.dto.response.WeeklyScheduleResponse;
import com.talent.management.features.attendance_makeup.service.ScheduleWeeklyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
@Tag(name = "Thời Khóa Biểu & Điểm Danh", description = "Quản lý thời khóa biểu tuần, chi tiết ca học, điểm danh học viên và tự động sinh lịch học")
public class ScheduleWeeklyController {

    private final ScheduleWeeklyService scheduleWeeklyService;

    @Operation(summary = "Lấy ma trận thời khóa biểu tuần theo ngày chỉ định (Tự động lọc theo Role)")
    @GetMapping("/weekly")
    public ResponseEntity<WeeklyScheduleResponse> getWeeklySchedule(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long branchId) {
        WeeklyScheduleResponse response = scheduleWeeklyService.getWeeklySchedule(date, studentId, teacherId, branchId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Lấy bảng điểm danh học viên của một ca học (Kèm cờ canEdit và lý do khóa nếu có)")
    @GetMapping("/lessons/{lessonId}/attendance")
    public ResponseEntity<LessonAttendanceSheetResponse> getLessonAttendanceSheet(@PathVariable Long lessonId) {
        LessonAttendanceSheetResponse response = scheduleWeeklyService.getLessonAttendanceSheet(lessonId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Lưu danh sách điểm danh cả lớp (Ràng buộc: Giáo viên chỉ được lưu trong ngày hôm đó)")
    @PostMapping("/lessons/{lessonId}/attendance")
    public ResponseEntity<Map<String, Object>> saveLessonAttendanceBatch(
            @PathVariable Long lessonId,
            @Valid @RequestBody BatchAttendanceSaveRequest request) {
        scheduleWeeklyService.saveLessonAttendanceBatch(lessonId, request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Lưu điểm danh thành công!",
                "lessonId", lessonId
        ));
    }

    @Operation(summary = "Tạo lớp học mới và tự động sinh danh sách buổi học (lessons) vào thời khóa biểu")
    @PostMapping("/classes")
    public ResponseEntity<Map<String, Object>> createClassWithLessons(
            @Valid @RequestBody CreateClassWithLessonsRequest request) {
        Long classId = scheduleWeeklyService.createClassWithLessons(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "message", "Tạo lớp học và sinh lịch tự động thành công!",
                "classId", classId
        ));
    }

    @Operation(summary = "Lấy danh sách giáo viên để làm bộ lọc")
    @GetMapping("/teachers")
    public ResponseEntity<List<Map<String, Object>>> getFilterTeachers() {
        return ResponseEntity.ok(scheduleWeeklyService.getFilterTeachers());
    }

    @Operation(summary = "Lấy danh sách lớp học để làm bộ lọc")
    @GetMapping("/classes")
    public ResponseEntity<List<Map<String, Object>>> getFilterClasses() {
        return ResponseEntity.ok(scheduleWeeklyService.getFilterClasses());
    }
}
