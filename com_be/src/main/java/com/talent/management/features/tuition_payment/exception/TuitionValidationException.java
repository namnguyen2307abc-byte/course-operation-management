package com.talent.management.features.tuition_payment.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** Field errors for the tuition form, without changing other features' error contracts. */
public class TuitionValidationException extends TuitionOperationException {
    private final Map<String, String> details;

    public TuitionValidationException(Map<String, String> details) {
        super(HttpStatus.BAD_REQUEST, "TUITION_PLAN_INVALID", String.join(" ", details.values()));
        this.details = Map.copyOf(details);
    }

    public Map<String, String> getDetails() {
        return details;
    }
}
