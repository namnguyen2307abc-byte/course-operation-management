package com.talent.management.features.tuition_payment.exception;

import com.talent.management.shared.exception.ApplicationException;
import com.talent.management.shared.exception.BusinessException;
import com.talent.management.shared.exception.ErrorResponses;
import com.talent.management.shared.exception.ExternalServiceException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/** Error formatting is limited to tuition controllers; other features retain their contract. */
@RestControllerAdvice(basePackages = "com.talent.management.features.tuition_payment.controller")
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@lombok.extern.slf4j.Slf4j
public class TuitionExceptionHandler {

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(Exception ex) {
        return error(403, "ACCESS_DENIED", "Bạn không có quyền thực hiện thao tác này", Map.of());
    }

    @ExceptionHandler(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(Exception ex) {
        return error(400, "INVALID_REQUEST", "Dữ liệu hoặc bộ lọc không hợp lệ", Map.of());
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause
                    instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException invalid) {
                StringBuilder field = new StringBuilder();
                Integer period = null;
                for (var part : invalid.getPath()) {
                    if (part.getFieldName() != null) {
                        if (!field.isEmpty()) field.append('.');
                        field.append(part.getFieldName());
                    } else if (part.getIndex() >= 0) {
                        field.append('[').append(part.getIndex()).append(']');
                        period = part.getIndex() + 1;
                    }
                }
                String message =
                        (period == null ? "" : "Kỳ " + period + ": ")
                                + (invalid.getTargetType() == java.time.LocalDate.class
                                        ? "hạn đóng không hợp lệ. Ngày phải tồn tại và có định dạng"
                                                + " yyyy-MM-dd."
                                        : "giá trị của trường "
                                                + field
                                                + " không đúng kiểu dữ liệu yêu cầu.");
                return error(
                        400,
                        "INVALID_FIELD_FORMAT",
                        message,
                        Map.of(field.isEmpty() ? "request" : field.toString(), message));
            }
            cause = cause.getCause();
        }
        return error(
                400,
                "INVALID_JSON",
                "Nội dung yêu cầu không phải JSON hợp lệ hoặc bị thiếu. Vui lòng tải lại trang và"
                        + " thử lại.",
                Map.of());
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataConflict(Exception ex) {
        return error(
                409,
                "DATA_CONFLICT",
                "Dữ liệu bị trùng hoặc liên kết đã thay đổi. Vui lòng tải lại và thử lại.",
                Map.of());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException ex) {
        return error(400, "BUSINESS_RULE_VIOLATION", ex.getMessage(), Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(
            ResponseStatusException ex) {
        return error(
                ex.getStatusCode().value(),
                "REQUEST_REJECTED",
                ex.getReason() != null ? ex.getReason() : "Yêu cầu bị từ chối.",
                Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new java.util.LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            String message = fieldError.getDefaultMessage();
            var period =
                    java.util.regex.Pattern.compile("installments\\[(\\d+)\\]")
                            .matcher(fieldError.getField());
            if (period.find())
                message = "Kỳ " + (Integer.parseInt(period.group(1)) + 1) + ": " + message;
            errors.putIfAbsent(fieldError.getField(), message);
        }
        return error(400, "VALIDATION_FAILED", String.join(" ", errors.values()), errors);
    }

    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(Exception ex) {
        return error(
                401, "AUTHENTICATION_FAILED", "Tài khoản hoặc mật khẩu không chính xác", Map.of());
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> handleMissingRoute() {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status)
                .body(routeError(status, "Không tìm thấy đường dẫn yêu cầu"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupportedMethod(
            HttpRequestMethodNotSupportedException ex) {
        HttpStatus status = HttpStatus.METHOD_NOT_ALLOWED;
        return ResponseEntity.status(status)
                .headers(ex.getHeaders())
                .body(routeError(status, "Phương thức HTTP không được hỗ trợ cho đường dẫn này"));
    }

    private Map<String, Object> routeError(HttpStatus status, String message) {
        return errorBody(status.value(), "HTTP_" + status.value(), message, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        log.error("Unhandled backend exception", ex);
        return error(
                500,
                "INTERNAL_ERROR",
                "Không thể xử lý yêu cầu. Vui lòng thử lại hoặc liên hệ trung tâm.",
                Map.of());
    }

    @ExceptionHandler(TuitionValidationException.class)
    public ResponseEntity<Map<String, Object>> handlePlanValidation(TuitionValidationException ex) {
        return error(400, ex.getCode(), ex.getMessage(), ex.getDetails());
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<Map<String, Object>> handleApplicationException(ApplicationException ex) {
        return error(ex.getStatusCode().value(), ex.getCode(), ex.getMessage(), Map.of());
    }

    @ExceptionHandler(org.springframework.web.client.RestClientException.class)
    public ResponseEntity<Map<String, Object>> handleUpstreamFailure(
            org.springframework.web.client.RestClientException ex) {
        log.warn("Upstream request failed", ex);
        var failure =
                new ExternalServiceException(
                        "UPSTREAM",
                        "Chưa xác định được kết quả từ dịch vụ thanh toán. Vui lòng kiểm tra lại"
                                + " trạng thái trước khi thu tiếp.",
                        ex);
        return error(
                failure.getStatusCode().value(), failure.getCode(), failure.getMessage(), Map.of());
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            jakarta.validation.ConstraintViolationException ex) {
        Map<String, String> errors = new java.util.LinkedHashMap<>();
        ex.getConstraintViolations()
                .forEach(v -> errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return error(400, "VALIDATION_FAILED", String.join(" ", errors.values()), errors);
    }

    private ResponseEntity<Map<String, Object>> error(
            int status, String code, String message, Map<String, String> details) {
        return ResponseEntity.status(status).body(errorBody(status, code, message, details));
    }

    private Map<String, Object> errorBody(
            int status, String code, String message, Map<String, String> details) {
        return ErrorResponses.body(status, code, message, details);
    }
}
