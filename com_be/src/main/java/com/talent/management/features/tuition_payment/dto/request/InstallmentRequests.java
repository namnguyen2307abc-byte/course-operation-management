package com.talent.management.features.tuition_payment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class InstallmentRequests {
    private InstallmentRequests() {}

    public record Reservation(
            @NotBlank String action,
            java.time.LocalDateTime deadline,
            @NotBlank @Size(max = 500) String reason,
            @AssertTrue(message = "Cần xác nhận đã liên hệ phụ huynh và kiểm tra chưa nhận tiền.")
                    boolean contactConfirmed) {
        public Reservation(String action, java.time.LocalDateTime deadline, String reason) {
            this(action, deadline, reason, false);
        }
    }

    public record Due(@NotNull @DecimalMin("0") BigDecimal amount, @NotNull LocalDate date) {}

    public record Plan(
            @NotEmpty @Size(max = 3) List<@Valid Due> installments,
            String discountMethod,
            BigDecimal discountValue,
            @Size(max = 255) String discountReason) {
        public Plan(List<Due> installments) {
            this(installments, null, null, null);
        }
    }

    public record Collection(
            @NotNull @DecimalMin("0") BigDecimal amount,
            BigDecimal cashGiven,
            @NotBlank @Size(max = 80) String idempotencyKey) {}

    public record Intent(@NotNull @DecimalMin("1") BigDecimal amount) {}

    public record ManualBankConfirmation(
            @NotNull @DecimalMin("1") BigDecimal amount,
            @NotBlank @Size(max = 120) String reference,
            @NotBlank @Size(max = 400) String reason,
            @AssertTrue(message = "Cần xác nhận đã kiểm tra tiền thực nhận tại ngân hàng.")
                    boolean bankVerified) {}

    public record Change(
            @NotNull Long installmentId,
            @NotNull LocalDate proposedDate,
            @NotBlank @Size(max = 500) String reason) {}

    public record Review(boolean approve, @NotBlank @Size(max = 500) String note) {}
}
