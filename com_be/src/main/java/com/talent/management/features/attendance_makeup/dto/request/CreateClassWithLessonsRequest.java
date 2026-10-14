package com.talent.management.features.attendance_makeup.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class CreateClassWithLessonsRequest {

    @NotNull(message = "courseId không được để trống")
    private Long courseId;

    @NotNull(message = "branchId không được để trống")
    private Long branchId;

    private Long roomId;

    private Long teacherId;

    @NotBlank(message = "Mã lớp không được để trống")
    private String classCode;

    @NotBlank(message = "Tên lớp không được để trống")
    private String className;

    @NotNull(message = "Sĩ số tối đa không được để trống")
    private Integer maxStudents;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    private LocalDate startDate;

    @NotNull(message = "Tổng số buổi học không được để trống")
    private Integer totalSessions;

    @NotEmpty(message = "Vui lòng chọn ít nhất một thứ trong tuần")
    private List<Integer> daysOfWeek; // 1 = Thứ 2, 2 = Thứ 3, ..., 7 = Chủ nhật

    @NotNull(message = "Giờ bắt đầu không được để trống")
    private LocalTime startTime;

    @NotNull(message = "Giờ kết thúc không được để trống")
    private LocalTime endTime;

    private String scheduleDescription;
}
