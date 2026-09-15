package com.talent.management.features.attendance_makeup.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbsenceRequestResponse {
    private Long id;
    private Long enrollmentId;
    private Long lessonId;
    private String reason;
    private String status;
}
