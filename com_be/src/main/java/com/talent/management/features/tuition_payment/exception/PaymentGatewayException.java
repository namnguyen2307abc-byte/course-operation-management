package com.talent.management.features.tuition_payment.exception;

import com.talent.management.shared.exception.ExternalServiceException;

/** A gateway error does not imply that the remote payment failed. */
public class PaymentGatewayException extends ExternalServiceException {
    public PaymentGatewayException(String message) {
        super("PAYMENT_GATEWAY", message, null);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super("PAYMENT_GATEWAY", message, cause);
    }
}
