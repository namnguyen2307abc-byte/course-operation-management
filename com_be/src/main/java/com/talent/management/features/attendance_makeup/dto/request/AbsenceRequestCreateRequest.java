package com.talent.management.features.attendance_makeup.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbsenceRequestCreateRequest {
    private Long enrollmentId;
    private Long lessonId;
    private String reason;
}
