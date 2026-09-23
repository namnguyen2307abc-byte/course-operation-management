package com.talent.management.features.tuition_payment.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReservationRequest {

    @NotNull(message = "ID học sinh không được để trống")
    private Long studentId;

    @NotNull(message = "ID lớp học không được để trống")
    private Long classId;

    private String notes;
}
