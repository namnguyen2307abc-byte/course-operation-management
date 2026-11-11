package com.talent.management.features.weekly_schedule.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSlotDto {
    private int slotNumber;
    private String slotName;
    private String timeRange;
    private LocalTime startTime;
    private LocalTime endTime;
    private String color;
    private String bgColor;
    private String borderColor;
}
