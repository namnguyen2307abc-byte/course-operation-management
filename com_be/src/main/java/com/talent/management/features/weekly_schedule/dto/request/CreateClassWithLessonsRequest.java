package com.talent.management.features.weekly_schedule.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @Size(max = 50, message = "Mã lớp không được vượt quá 50 ký tự")
    private String classCode;

    @NotBlank(message = "Tên lớp không được để trống")
    @Size(max = 150, message = "Tên lớp không được vượt quá 150 ký tự")
    private String className;

    @NotNull(message = "Sĩ số tối đa không được để trống")
    @Min(value = 1, message = "Sĩ số tối đa phải từ 1 học viên trở lên")
    @Max(value = 100, message = "Sĩ số tối đa không được vượt quá 100 học viên")
    private Integer maxStudents;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    private LocalDate startDate;

    @NotNull(message = "Tổng số buổi học không được để trống")
    @Min(value = 1, message = "Tổng số buổi học phải từ 1 buổi trở lên")
    @Max(value = 60, message = "Tổng số buổi học không được vượt quá 60 buổi")
    private Integer totalSessions;

    @NotEmpty(message = "Vui lòng chọn ít nhất một thứ trong tuần")
    private List<Integer> daysOfWeek; // 1 = Thứ 2, 2 = Thứ 3, ..., 7 = Chủ nhật

    @NotNull(message = "Giờ bắt đầu không được để trống")
    private LocalTime startTime;

    @NotNull(message = "Giờ kết thúc không được để trống")
    private LocalTime endTime;

    @Size(max = 255, message = "Mô tả lịch học không được vượt quá 255 ký tự")
    private String scheduleDescription;
}
