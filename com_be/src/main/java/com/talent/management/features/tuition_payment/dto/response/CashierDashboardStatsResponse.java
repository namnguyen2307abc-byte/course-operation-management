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
public class CashierDashboardStatsResponse {

    private long pendingInvoicesCount;
    private long paidTodayCount;
    private BigDecimal revenueToday;
    private long totalStudentsEnrolled;
}
