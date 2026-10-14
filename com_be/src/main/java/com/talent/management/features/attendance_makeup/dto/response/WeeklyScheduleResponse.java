package com.talent.management.features.attendance_makeup.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyScheduleResponse {
    private int weekNumber;
    private int year;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate currentDate;
    private String currentUserRole;
    private String currentUsername;
    private List<TimeSlotDto> slots;
    private List<LessonSlotItemResponse> lessons;
}
