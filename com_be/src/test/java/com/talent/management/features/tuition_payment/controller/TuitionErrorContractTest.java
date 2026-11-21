package com.talent.management.features.tuition_payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.talent.management.features.tuition_payment.exception.PaymentGatewayException;
import com.talent.management.features.tuition_payment.exception.TuitionExceptionHandler;
import com.talent.management.features.tuition_payment.exception.TuitionOperationException;
import com.talent.management.features.tuition_payment.service.PaymentResultService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.net.http.HttpTimeoutException;
import java.util.Map;

class TuitionErrorContractTest {
    private final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(new FailureController())
                    .setControllerAdvice(new TuitionExceptionHandler())
                    .build();

    @RestController
    static class FailureController {
        @GetMapping("/missing")
        public void missing() {
            throw new TuitionOperationException(
                    HttpStatus.NOT_FOUND, "TUITION_INVOICE_NOT_FOUND", "Không tìm thấy hóa đơn.");
        }

        @GetMapping("/unexpected")
        public void unexpected() {
            throw new IllegalStateException("SQL password=private-internal-value");
        }

        @GetMapping("/timeout")
        public void timeout() {
            throw new PaymentGatewayException(
                    "Chưa xác định kết quả thanh toán.",
                    new HttpTimeoutException("internal upstream timeout"));
        }

        record Input(@NotBlank String reason) {}

        @PostMapping("/schedule")
        public void schedule(
                @Valid @RequestBody
                        com.talent.management.features.tuition_payment.dto.request
                                        .ConfigureTuitionPlanRequest
                                input) {
            com.talent.management.features.tuition_payment.policy.TuitionSchedulePolicy.validate(
                    input.installments(),
                    new java.math.BigDecimal("10000"),
                    java.time.LocalDate.of(2026, 11, 20),
                    java.time.LocalDate.of(2026, 12, 20));
        }

        @PostMapping("/validate")
        public void validate(@Valid @RequestBody Input input) {}
    }

    @Test
    void expectedFailureRetainsStatusAndStableCode() throws Exception {
        mvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TUITION_INVOICE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void unexpectedFailureNeverLeaksInternalExceptionMessage() throws Exception {
        var response =
                mvc.perform(get("/unexpected"))
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                        .andReturn()
                        .getResponse();
        assertThat(response.getContentAsString())
                .doesNotContain("private-internal-value", "SQL password");
    }

    @Test
    void timeoutIsDistinctFromADeclinedPayment() throws Exception {
        mvc.perform(get("/timeout"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_TIMEOUT"));
    }

    @Test
    void invalidFieldsRemainReadableByTheExistingFrontend() throws Exception {
        mvc.perform(
                        post("/validate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.reason").isString());
    }

    @Test
    void scheduleErrorsIncludeExactPeriodAndAllowedEndDate() throws Exception {
        mvc.perform(
                        post("/schedule")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"installments\":[{\"amount\":10000,\"date\":\"2026-12-21\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TUITION_PLAN_INVALID"))
                .andExpect(
                        jsonPath("$.details['installments[0].date']")
                                .value(org.hamcrest.Matchers.containsString("20/12/2026")));
    }

    @Test
    void impossibleCalendarDateIdentifiesItsField() throws Exception {
        mvc.perform(
                        post("/schedule")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"installments\":[{\"amount\":10000,\"date\":\"2026-13-20\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_FORMAT"))
                .andExpect(
                        jsonPath("$.details['installments[0].date']")
                                .value(org.hamcrest.Matchers.containsString("Kỳ 1")));
    }

    @Test
    void publicWebhookStillRoutesToSignatureValidationWithoutAUserToken() throws Exception {
        PaymentResultService results = mock(PaymentResultService.class);
        when(results.webhook(anyMap()))
                .thenThrow(
                        new TuitionOperationException(
                                HttpStatus.BAD_REQUEST,
                                "PAYMENT_SIGNATURE_INVALID",
                                "Chữ ký PayOS không hợp lệ."));
        var webhook =
                MockMvcBuilders.standaloneSetup(new PayosWebhookController(results))
                        .setControllerAdvice(new TuitionExceptionHandler())
                        .build();
        webhook.perform(
                        post("/api/tuition-payment/payos-webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"signature\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_SIGNATURE_INVALID"));
        verify(results).webhook(Map.of("signature", "invalid"));
    }
}
