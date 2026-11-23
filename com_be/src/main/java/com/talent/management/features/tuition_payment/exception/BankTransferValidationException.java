package com.talent.management.features.tuition_payment.exception;

import java.util.Map;

public class BankTransferValidationException extends TuitionValidationException {
    public BankTransferValidationException(Map<String, String> details) {
        super("BANK_TRANSFER_INVALID", details);
    }
}
