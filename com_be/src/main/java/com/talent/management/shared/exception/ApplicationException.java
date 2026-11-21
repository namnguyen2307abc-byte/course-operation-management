package com.talent.management.shared.exception;

import org.springframework.http.HttpStatusCode;

/** An expected application failure with a stable code and a safe client message. */
public class ApplicationException extends RuntimeException {
    private final HttpStatusCode status;
    private final String code;

    public ApplicationException(HttpStatusCode status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatusCode getStatusCode() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getReason() {
        return getMessage();
    }
}
