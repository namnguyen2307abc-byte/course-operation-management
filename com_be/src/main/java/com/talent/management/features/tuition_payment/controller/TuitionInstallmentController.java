package com.talent.management.features.tuition_payment.controller;

import com.talent.management.features.tuition_payment.dto.request.InstallmentRequests.*;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.*;
import com.talent.management.features.tuition_payment.dto.response.PaymentReceiptResponse;
import com.talent.management.features.tuition_payment.service.*;

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
    private final TuitionReservationService reservations;

    @PostMapping("/{id}/reservation")
    @PreAuthorize("hasRole('ADMIN')")
    public InvoiceView reservation(@PathVariable Long id, @Valid @RequestBody Reservation input) {
        return reservations.decide(id, input);
    }

    private final TuitionPlanService plans;
    private final PaymentPostingService posting;
    private final InstallmentGatewayService gateway;
    private final PlanChangeService changes;
    private final TuitionReversalService reversals;

    public record Reason(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500)
                    String reason) {}

    @GetMapping("/receivables")
    public List<InvoiceView> list() {
        return plans.list();
    }

    @GetMapping("/{id}/plan")
    public InvoiceView view(@PathVariable Long id) {
        return plans.view(id);
    }

    @PostMapping("/{id}/plan")
    public InvoiceView plan(@PathVariable Long id, @Valid @RequestBody Plan input) {
        return plans.configure(id, input);
    }

    @PostMapping("/{id}/collections")
    public PaymentReceiptResponse collect(
            @PathVariable Long id, @Valid @RequestBody Collection input) {
        gateway.prepareCashPayment(id);
        return posting.collectReceipt(
                id, input.amount(), input.cashGiven(), input.idempotencyKey());
    }

    @PostMapping("/{id}/payment-intents")
    public IntentView intent(@PathVariable Long id, @Valid @RequestBody Intent input) {
        return gateway.create(id, input.amount());
    }

    @GetMapping("/payment-intents/{id}")
    public IntentView intentStatus(@PathVariable Long id) {
        return gateway.status(id);
    }

    @PostMapping("/payment-intents/{id}/cancel")
    public void cancel(@PathVariable Long id) {
        gateway.cancel(id);
    }

    @PostMapping("/payment-intents/{id}/confirm-bank")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','BRANCH_MANAGER')")
    public PaymentReceiptResponse confirmBank(
            @PathVariable Long id, @Valid @RequestBody ManualBankConfirmation input) {
        return gateway.confirmBank(id, input);
    }

    @PostMapping("/payment-intents/{id}/simulate")
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentReceiptResponse simulate(@PathVariable Long id) {
        return gateway.simulate(id);
    }

    @PostMapping("/{id}/plan-change-requests")
    public void change(@PathVariable Long id, @Valid @RequestBody Change input) {
        changes.request(id, input);
    }

    @GetMapping("/plan-change-requests")
    public List<ChangeView> changes() {
        return changes.list();
    }

    @PostMapping("/plan-change-requests/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER')")
    public void review(@PathVariable Long id, @Valid @RequestBody Review input) {
        changes.review(id, input);
    }

    @GetMapping("/overdue-follow-ups")
    @PreAuthorize("hasRole('ADMIN')")
    public List<OverdueContact> followUps() {
        return plans.overdueContacts();
    }

    @GetMapping("/reversals")
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER')")
    public List<TuitionReversalService.View> reversals() {
        return reversals.list();
    }

    @PostMapping("/payments/{id}/reversal")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','BRANCH_MANAGER')")
    public void reversal(@PathVariable Long id, @Valid @RequestBody Reason input) {
        reversals.request(id, input.reason());
    }

    @PostMapping("/reversals/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER')")
    public void reviewReversal(@PathVariable Long id, @Valid @RequestBody Review input) {
        reversals.review(id, input);
    }
}
