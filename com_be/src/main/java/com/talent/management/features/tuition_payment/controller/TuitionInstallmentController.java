package com.talent.management.features.tuition_payment.controller;

import com.talent.management.features.tuition_payment.dto.request.CancelReservationRequest;
import com.talent.management.features.tuition_payment.dto.request.CollectCashRequest;
import com.talent.management.features.tuition_payment.dto.request.ConfigureTuitionPlanRequest;
import com.talent.management.features.tuition_payment.dto.request.ConfirmBankPaymentRequest;
import com.talent.management.features.tuition_payment.dto.request.CreatePaymentIntentRequest;
import com.talent.management.features.tuition_payment.dto.request.RequestDueDateChangeRequest;
import com.talent.management.features.tuition_payment.dto.request.ReviewTuitionRequest;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.ChangeView;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.IntentView;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.InvoiceView;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.OverdueContact;
import com.talent.management.features.tuition_payment.dto.response.PaymentReceiptResponse;
import com.talent.management.features.tuition_payment.service.CashCollectionService;
import com.talent.management.features.tuition_payment.service.PaymentIntentService;
import com.talent.management.features.tuition_payment.service.PaymentResultService;
import com.talent.management.features.tuition_payment.service.PlanChangeService;
import com.talent.management.features.tuition_payment.service.TuitionPlanService;
import com.talent.management.features.tuition_payment.service.TuitionQueryService;
import com.talent.management.features.tuition_payment.service.TuitionReservationService;
import com.talent.management.features.tuition_payment.service.TuitionReversalService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tuition-payment")
@PreAuthorize("hasAnyRole('ADMIN','STAFF','BRANCH_MANAGER')")
public class TuitionInstallmentController {
    private final TuitionReservationService tuitionReservationService;

    @PostMapping("/{id}/reservation")
    @PreAuthorize("hasRole('ADMIN')")
    public InvoiceView reservation(
            @PathVariable Long id, @Valid @RequestBody CancelReservationRequest input) {
        return tuitionReservationService.decide(id, input);
    }

    private final TuitionPlanService tuitionPlanService;
    private final CashCollectionService cashCollectionService;
    private final TuitionQueryService tuitionQueryService;
    private final PaymentResultService paymentResultService;
    private final PaymentIntentService paymentIntentService;
    private final PlanChangeService planChangeService;
    private final TuitionReversalService tuitionReversalService;

    public record Reason(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500)
                    String reason) {}

    @GetMapping("/receivables")
    public List<InvoiceView> list() {
        return tuitionQueryService.list();
    }

    @GetMapping("/{id}/plan")
    public InvoiceView view(@PathVariable Long id) {
        return tuitionQueryService.view(id);
    }

    @PostMapping("/{id}/plan")
    public InvoiceView plan(
            @PathVariable Long id, @Valid @RequestBody ConfigureTuitionPlanRequest input) {
        return tuitionPlanService.configure(id, input);
    }

    @PostMapping("/{id}/collections")
    public PaymentReceiptResponse collect(
            @PathVariable Long id, @Valid @RequestBody CollectCashRequest input) {
        return cashCollectionService.collectReceipt(
                id, input.amount(), input.cashGiven(), input.idempotencyKey());
    }

    @PostMapping("/{id}/payment-intents")
    public IntentView intent(
            @PathVariable Long id, @Valid @RequestBody CreatePaymentIntentRequest input) {
        return paymentIntentService.create(id, input.amount());
    }

    @GetMapping("/payment-intents/{id}")
    public IntentView intentStatus(@PathVariable Long id) {
        return paymentIntentService.status(id);
    }

    @PostMapping("/payment-intents/{id}/cancel")
    public IntentView cancel(@PathVariable Long id) {
        paymentIntentService.cancel(id);
        return paymentIntentService.status(id);
    }

    @PostMapping("/payment-intents/{id}/confirm-bank")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','BRANCH_MANAGER')")
    public PaymentReceiptResponse confirmBank(
            @PathVariable Long id, @Valid @RequestBody ConfirmBankPaymentRequest input) {
        return paymentResultService.confirmBank(id, input);
    }

    @PostMapping("/payment-intents/{id}/simulate")
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentReceiptResponse simulate(@PathVariable Long id) {
        return paymentIntentService.simulate(id);
    }

    @PostMapping("/{id}/plan-change-requests")
    public void change(
            @PathVariable Long id, @Valid @RequestBody RequestDueDateChangeRequest input) {
        planChangeService.request(id, input);
    }

    @GetMapping("/plan-change-requests")
    public List<ChangeView> changes() {
        return planChangeService.list();
    }

    @PostMapping("/plan-change-requests/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER')")
    public void review(@PathVariable Long id, @Valid @RequestBody ReviewTuitionRequest input) {
        planChangeService.review(id, input);
    }

    @GetMapping("/overdue-follow-ups")
    @PreAuthorize("hasRole('ADMIN')")
    public List<OverdueContact> followUps() {
        return tuitionQueryService.overdueContacts();
    }

    @GetMapping("/reversals")
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER')")
    public List<TuitionReversalService.View> reversals() {
        return tuitionReversalService.list();
    }

    @PostMapping("/payments/{id}/reversal")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','BRANCH_MANAGER')")
    public void reversal(@PathVariable Long id, @Valid @RequestBody Reason input) {
        tuitionReversalService.request(id, input.reason());
    }

    @PostMapping("/reversals/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER')")
    public void reviewReversal(
            @PathVariable Long id, @Valid @RequestBody ReviewTuitionRequest input) {
        tuitionReversalService.review(id, input);
    }
}
