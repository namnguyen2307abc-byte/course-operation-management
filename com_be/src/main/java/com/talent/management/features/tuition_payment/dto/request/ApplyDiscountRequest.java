package com.talent.management.features.tuition_payment.dto.request;

import com.talent.management.shared.enums.DiscountType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyDiscountRequest {

    @NotNull(message = "Loại miễn giảm không được để trống")
    private DiscountType discountType;

    private String discountMethod; // PERCENTAGE hoặc FIXED_AMOUNT

    private BigDecimal discountValue; // Số % hoặc số tiền cụ thể

    private String discountReason; // Lý do giảm hoặc mã voucher

    private String couponCode; // Mã ưu đãi nếu có
}
