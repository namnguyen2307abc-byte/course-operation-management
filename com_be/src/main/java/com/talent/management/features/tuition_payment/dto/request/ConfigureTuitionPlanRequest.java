package com.talent.management.features.tuition_payment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record ConfigureTuitionPlanRequest(
        @NotEmpty(message = "Vui lòng chọn ít nhất 1 kỳ thanh toán.")
                @Size(max = 3, message = "Chỉ được chia tối đa 3 kỳ thanh toán.")
                List<
                                @NotNull(message = "Không được để trống kỳ thanh toán.") @Valid
                                InstallmentDueRequest>
                        installments,
        String discountMethod,
        BigDecimal discountValue,
        @Size(max = 255) String discountReason) {
    public ConfigureTuitionPlanRequest(List<InstallmentDueRequest> installments) {
        this(installments, null, null, null);
    }
}
