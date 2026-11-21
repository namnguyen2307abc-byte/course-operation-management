package com.talent.management.features.tuition_payment.exception;

import com.talent.management.shared.exception.ApplicationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/** Expected tuition failures, independent of controllers and other services. */
public class TuitionOperationException extends ApplicationException {
    public TuitionOperationException(HttpStatusCode status, String message) {
        this(status, "TUITION_" + status.value(), message);
    }

    public TuitionOperationException(HttpStatusCode status, String code, String message) {
        super(status, code, message);
    }

    public static TuitionOperationException conflict(String message) {
        return new TuitionOperationException(
                HttpStatus.CONFLICT, "TUITION_STATE_CONFLICT", message);
    }

    public static TuitionOperationException notFound() {
        return new TuitionOperationException(
                HttpStatus.NOT_FOUND,
                "TUITION_RECORD_NOT_FOUND",
                "Không tìm thấy dữ liệu học phí yêu cầu.");
    }
}
