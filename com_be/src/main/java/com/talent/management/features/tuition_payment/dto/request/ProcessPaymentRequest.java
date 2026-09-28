package com.talent.management.features.tuition_payment.dto.request;

import com.talent.management.shared.enums.DiscountType;
import com.talent.management.shared.enums.PaymentMethod;
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
public class ProcessPaymentRequest {

    @NotNull(message = "ID hóa đơn không được để trống")
    private Long invoiceId;

    @NotNull(message = "Phương thức thanh toán không được để trống")
    private PaymentMethod paymentMethod;

    @Builder.Default
    private DiscountType discountType = DiscountType.NONE;

    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    private String discountReason;

    @NotNull(message = "Số tiền thực thu không được để trống")
    private BigDecimal finalAmount;

    private BigDecimal cashGiven; // Số tiền phụ huynh đưa tại quầy

    private String bankTransactionId; // Mã chuẩn chi / mã GD ngân hàng

    private String note;
}
