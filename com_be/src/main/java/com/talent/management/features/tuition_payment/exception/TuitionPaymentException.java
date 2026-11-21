package com.talent.management.features.tuition_payment.exception;

import org.springframework.http.HttpStatus;

public class TuitionPaymentException extends TuitionOperationException {
    public TuitionPaymentException(String message) {
        super(HttpStatus.BAD_REQUEST, "TUITION_REQUEST_INVALID", message);
    }
}
