package com.talent.management.features.tuition_payment.service.impl;

import static com.talent.management.features.tuition_payment.exception.TuitionOperationException.conflict;

import com.talent.management.features.tuition_payment.dto.response.PaymentReceiptResponse;
import com.talent.management.features.tuition_payment.exception.TuitionOperationException;
import com.talent.management.features.tuition_payment.mapper.TuitionPaymentMapper;
import com.talent.management.features.tuition_payment.policy.CashTenderPolicy;
import com.talent.management.features.tuition_payment.repository.PaymentRepository;
import com.talent.management.features.tuition_payment.repository.TuitionLedgerRepository;
import com.talent.management.features.tuition_payment.service.CashCollectionService;
import com.talent.management.features.tuition_payment.service.PaymentIntentService;
import com.talent.management.features.tuition_payment.service.PaymentPostingService;
import com.talent.management.features.tuition_payment.service.TuitionLedgerService;
import com.talent.management.shared.entity.Invoice;
import com.talent.management.shared.entity.Payment;
import com.talent.management.shared.entity.TuitionPlan;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.PaymentMethod;
import com.talent.management.shared.service.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CashCollectionServiceImpl implements CashCollectionService {
    private final TuitionLedgerRepository ledgerRepository;
    private final TuitionLedgerService tuitionLedgerService;
    private final PaymentPostingService paymentPostingService;
    private final PaymentIntentService paymentIntentService;
    private final TransactionTemplate transactionTemplate;
    private final PaymentRepository payments;
    private final CurrentUserService currentUserService;
    private final TuitionPaymentMapper mapper;

    @Override
    public PaymentReceiptResponse collectReceipt(
            Long invoiceId, BigDecimal amount, BigDecimal cashGiven, String key) {
        var replay =
                transactionTemplate.execute(
                        status -> replayCashReceipt(invoiceId, amount, cashGiven, key));
        if (replay.isPresent()) return replay.get();
        // Gateway calls happen before the cash transaction, never while an invoice lock is held.
        paymentIntentService.prepareCashPayment(invoiceId, amount, cashGiven);
        return transactionTemplate.execute(
                status ->
                        mapper.toPaymentReceiptResponse(
                                cash(invoiceId, amount, cashGiven, key), null, null));
    }

    @Transactional
    @Override
    public Payment cash(Long invoiceId, BigDecimal amount, BigDecimal cashGiven, String key) {
        Invoice invoice = tuitionLedgerService.lock(invoiceId);
        User actor = currentUserService.getCurrentUser();
        Optional<Payment> duplicate = existingCashPayment(invoiceId, amount, cashGiven, key, actor);
        if (duplicate.isPresent()) return duplicate.get();
        TuitionPlan plan = tuitionLedgerService.ensure(invoice);
        if (ledgerRepository.hasIntents(invoiceId, List.of("CREATING", "PENDING", "UNCERTAIN")))
            throw conflict(
                    "QR chưa đóng thành công; cần xác minh ngân hàng trước khi thu tiền mặt.");
        tuitionLedgerService.requireCurrentInstallment(invoice, plan, amount);
        CashTenderPolicy.quote(amount, cashGiven);
        return paymentPostingService.post(
                invoice,
                plan,
                amount,
                PaymentMethod.CASH_AT_DESK,
                actor,
                "CASH-" + key,
                null,
                cashGiven);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<PaymentReceiptResponse> replayCashReceipt(
            Long invoiceId, BigDecimal amount, BigDecimal cashGiven, String key) {
        return existingCashPayment(
                        invoiceId, amount, cashGiven, key, currentUserService.getCurrentUser())
                .map(payment -> mapper.toPaymentReceiptResponse(payment, null, null));
    }

    private Optional<Payment> existingCashPayment(
            Long invoiceId, BigDecimal amount, BigDecimal cashGiven, String key, User actor) {
        if (key == null || key.isBlank() || key.length() > 80)
            throw conflict("Thiếu mã chống gửi lặp.");
        String scoped = "CASH-" + key;
        Optional<Payment> duplicate = payments.findByRequestKey(scoped);
        if (duplicate.isPresent()) {
            Payment old = duplicate.get();
            if (!old.getInvoice().getId().equals(invoiceId)
                    || amount == null
                    || old.getAmount().compareTo(amount) != 0
                    || cashGiven == null
                    || old.getCashGiven().compareTo(cashGiven) != 0
                    || old.getCashier() == null
                    || !old.getCashier().getId().equals(actor.getId())) {
                throw new TuitionOperationException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "PAYMENT_IDEMPOTENCY_CONFLICT",
                        "Mã yêu cầu đã dùng với nội dung khác.");
            }
        }
        return duplicate;
    }
}
