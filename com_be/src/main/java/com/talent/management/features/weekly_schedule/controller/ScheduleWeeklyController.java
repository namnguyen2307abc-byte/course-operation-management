package com.talent.management.features.weekly_schedule.controller;

import com.talent.management.features.weekly_schedule.dto.request.CreateClassWithLessonsRequest;
import com.talent.management.features.weekly_schedule.dto.response.WeeklyScheduleResponse;
import com.talent.management.features.weekly_schedule.service.ScheduleWeeklyService;
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
@Tag(name = "Thời Khóa Biểu Tuần (Weekly Schedule)", description = "Quản lý thời khóa biểu tuần, ma trận ca học và tự động sinh lịch học")
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

    @Operation(summary = "Tạo lớp học mới và tự động sinh danh sách buổi học (lessons)")
    @PostMapping("/classes")
    public ResponseEntity<Map<String, Object>> createClassWithLessons(
            @Valid @RequestBody CreateClassWithLessonsRequest request) {
        Long classId = scheduleWeeklyService.createClassWithLessons(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "message", "Tạo lớp học và sinh thời khóa biểu tự động thành công",
                "classId", classId
        ));
    }

    @Operation(summary = "Lấy danh sách giáo viên để lọc trên thời khóa biểu")
    @GetMapping("/teachers")
    public ResponseEntity<List<Map<String, Object>>> getFilterTeachers() {
        return ResponseEntity.ok(scheduleWeeklyService.getFilterTeachers());
    }

    @Operation(summary = "Lấy danh sách lớp học để lọc trên thời khóa biểu")
    @GetMapping("/classes")
    public ResponseEntity<List<Map<String, Object>>> getFilterClasses() {
        return ResponseEntity.ok(scheduleWeeklyService.getFilterClasses());
    }
}
