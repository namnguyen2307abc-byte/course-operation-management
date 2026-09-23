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
public class VietQrResponse {

    private String qrImageUrl;
    private String bankId;
    private String bankName;
    private String accountNo;
    private String accountName;
    private BigDecimal amount;
    private String transferContent;
    private String paymentDescription;
}
