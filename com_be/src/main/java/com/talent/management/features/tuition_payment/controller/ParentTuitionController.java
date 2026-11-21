package com.talent.management.features.tuition_payment.controller;

import com.talent.management.features.tuition_payment.dto.request.CreatePaymentIntentRequest;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.IntentView;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.InvoiceView;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.NoticeView;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.Receipt;
import com.talent.management.features.tuition_payment.service.PaymentIntentService;
import com.talent.management.features.tuition_payment.service.TuitionNotificationService;
import com.talent.management.features.tuition_payment.service.TuitionQueryService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/parent-tuition")
@PreAuthorize("hasRole('PARENT')")
public class ParentTuitionController {
    private final TuitionQueryService tuitionQueryService;
    private final PaymentIntentService paymentIntentService;
    private final TuitionNotificationService tuitionNotificationService;

    @GetMapping("/invoices")
    public List<InvoiceView> list() {
        return tuitionQueryService.list();
    }

    @GetMapping("/invoices/{id}")
    public InvoiceView detail(@PathVariable Long id) {
        return tuitionQueryService.view(id);
    }

    @GetMapping("/invoices/{id}/receipts")
    public List<Receipt> receipts(@PathVariable Long id) {
        return tuitionQueryService.view(id).receipts();
    }

    @PostMapping("/invoices/{id}/payment-intents")
    public IntentView intent(
            @PathVariable Long id, @Valid @RequestBody CreatePaymentIntentRequest input) {
        return paymentIntentService.create(id, input.amount());
    }

    @GetMapping("/payment-intents/{id}")
    public IntentView status(@PathVariable Long id) {
        return paymentIntentService.status(id);
    }

    @PostMapping("/payment-intents/{id}/cancel")
    public IntentView cancel(@PathVariable Long id) {
        paymentIntentService.cancel(id);
        return paymentIntentService.status(id);
    }

    @GetMapping("/notifications")
    public List<NoticeView> notices() {
        return tuitionNotificationService.mine();
    }

    @PatchMapping("/notifications/{id}/read")
    public void read(@PathVariable Long id) {
        tuitionNotificationService.markRead(id);
    }
}
