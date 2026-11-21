package com.talent.management.features.tuition_payment.dto.request;

import jakarta.validation.constraints.*;

public record CancelReservationRequest(
        @NotBlank String action,
        java.time.LocalDateTime deadline,
        @NotBlank @Size(max = 500) String reason,
        @AssertTrue(message = "Cần xác nhận đã liên hệ phụ huynh và kiểm tra chưa nhận tiền.")
                boolean contactConfirmed) {
    public CancelReservationRequest(
            String action, java.time.LocalDateTime deadline, String reason) {
        this(action, deadline, reason, false);
    }
}
