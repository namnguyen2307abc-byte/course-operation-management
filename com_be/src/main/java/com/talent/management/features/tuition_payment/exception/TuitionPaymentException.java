package com.talent.management.features.tuition_payment.exception;

import com.talent.management.shared.exception.BusinessException;

public class TuitionPaymentException extends BusinessException {
    public TuitionPaymentException(String message) {
        super(message);
    }
}
