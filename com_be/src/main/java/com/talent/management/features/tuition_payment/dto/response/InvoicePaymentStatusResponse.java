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
public class InvoicePaymentStatusResponse {
    private Long invoiceId;
    private String invoiceCode;
    private String status; // "PAID", "UNPAID"
    private boolean isPaid;
    private BigDecimal finalAmount;
    private PaymentReceiptResponse receipt;
}
