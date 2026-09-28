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
public class PayOSPaymentLinkResponse {
    private Long orderCode;
    private String checkoutUrl;
    private String qrCode;
    private String paymentLinkId;
    private BigDecimal amount;
    private String accountName;
    private String accountNumber;
    private String bin;
    private String transferContent;
    private String status;
    private boolean simulated;
}
