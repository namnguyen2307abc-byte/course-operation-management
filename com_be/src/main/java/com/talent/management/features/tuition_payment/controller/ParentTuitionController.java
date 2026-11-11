package com.talent.management.features.tuition_payment.controller;

import com.talent.management.features.tuition_payment.dto.request.InstallmentRequests.Intent;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.*;
import com.talent.management.features.tuition_payment.service.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/parent-tuition")
@PreAuthorize("hasRole('PARENT')")
public class ParentTuitionController {
    private final TuitionPlanService plans;
    private final InstallmentGatewayService gateway;
    private final TuitionNotificationService notices;

    @GetMapping("/invoices")
    public List<InvoiceView> list() {
        return plans.list();
    }

    @GetMapping("/invoices/{id}")
    public InvoiceView detail(@PathVariable Long id) {
        return plans.view(id);
    }

    @GetMapping("/invoices/{id}/receipts")
    public List<Receipt> receipts(@PathVariable Long id) {
        return plans.view(id).receipts();
    }

    @PostMapping("/invoices/{id}/payment-intents")
    public IntentView intent(@PathVariable Long id, @Valid @RequestBody Intent input) {
        return gateway.create(id, input.amount());
    }

    @GetMapping("/payment-intents/{id}")
    public IntentView status(@PathVariable Long id) {
        return gateway.status(id);
    }

    @PostMapping("/payment-intents/{id}/cancel")
    public void cancel(@PathVariable Long id) {
        gateway.cancel(id);
    }

    @GetMapping("/notifications")
    public List<NoticeView> notices() {
        return notices.mine();
    }

    @PatchMapping("/notifications/{id}/read")
    public void read(@PathVariable Long id) {
        notices.markRead(id);
    }
}
