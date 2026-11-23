package com.talent.management.features.tuition_payment.service;

import com.talent.management.features.tuition_payment.dto.response.PaymentReceiptResponse;
import com.talent.management.features.tuition_payment.gateway.VerifiedBankTransfer;

public interface BankTransferService {
    PaymentReceiptResponse receive(VerifiedBankTransfer transfer);
}
