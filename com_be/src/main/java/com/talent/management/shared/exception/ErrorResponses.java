package com.talent.management.shared.exception;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/** Shared wire format for MVC exceptions and security failures outside MVC. */
public final class ErrorResponses {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ErrorResponses() {}

    public static Map<String, Object> body(
            int status, String code, String message, Map<String, String> details) {
        return Map.of(
                "timestamp",
                LocalDateTime.now().toString(),
                "status",
                status,
                "code",
                code,
                "error",
                HttpStatus.valueOf(status).getReasonPhrase(),
                "message",
                message,
                "details",
                details);
    }

    public static void write(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        JSON.writeValue(response.getWriter(), body(status, code, message, Map.of()));
    }
}
