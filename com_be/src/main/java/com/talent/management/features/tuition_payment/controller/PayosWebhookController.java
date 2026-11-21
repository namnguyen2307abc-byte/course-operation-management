package com.talent.management.features.tuition_payment.controller;

import com.talent.management.features.tuition_payment.dto.response.PaymentReceiptResponse;
import com.talent.management.features.tuition_payment.service.PaymentResultService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Public transport endpoint; the service verifies the PayOS signature before applying money. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tuition-payment")
public class PayosWebhookController {
    private final PaymentResultService paymentResultService;

    @PostMapping("/payos-webhook")
    public PaymentReceiptResponse receive(@RequestBody Map<String, Object> payload) {
        return paymentResultService.webhook(payload);
    }
}
