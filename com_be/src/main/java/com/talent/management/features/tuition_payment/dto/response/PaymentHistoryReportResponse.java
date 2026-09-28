package com.talent.management.features.tuition_payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHistoryReportResponse {
    @Builder.Default
    private PaymentSummaryResponse summary = new PaymentSummaryResponse();
    @Builder.Default
    private List<PaymentReceiptResponse> payments = new ArrayList<>();
}
