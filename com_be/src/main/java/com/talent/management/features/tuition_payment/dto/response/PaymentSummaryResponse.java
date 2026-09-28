package com.talent.management.features.tuition_payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSummaryResponse {
    @Builder.Default
    private BigDecimal totalCashAmount = BigDecimal.ZERO;
    @Builder.Default
    private long totalCashCount = 0;

    @Builder.Default
    private BigDecimal totalBankAmount = BigDecimal.ZERO;
    @Builder.Default
    private long totalBankCount = 0;

    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    @Builder.Default
    private long totalTransactions = 0;
}
