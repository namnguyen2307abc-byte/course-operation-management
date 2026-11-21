package com.talent.management.shared.exception;

import org.springframework.http.HttpStatus;

public class ExternalServiceException extends ApplicationException {
    public ExternalServiceException(String prefix, String message, Throwable cause) {
        super(
                isTimeout(cause) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY,
                prefix + (isTimeout(cause) ? "_TIMEOUT" : "_ERROR"),
                message);
        if (cause != null) initCause(cause);
    }

    private static boolean isTimeout(Throwable cause) {
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current instanceof java.net.http.HttpTimeoutException
                    || current instanceof java.net.SocketTimeoutException) return true;
        }
        return false;
    }
}
