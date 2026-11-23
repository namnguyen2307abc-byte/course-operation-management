package com.talent.management.features.tuition_payment.service.impl;

import com.talent.management.features.tuition_payment.dto.response.PaymentReceiptResponse;
import com.talent.management.features.tuition_payment.enums.*;
import com.talent.management.features.tuition_payment.exception.TuitionOperationException;
import com.talent.management.features.tuition_payment.gateway.VerifiedBankTransfer;
import com.talent.management.features.tuition_payment.mapper.TuitionPaymentMapper;
import com.talent.management.features.tuition_payment.policy.BankTransferPolicy;
import com.talent.management.features.tuition_payment.repository.*;
import com.talent.management.features.tuition_payment.service.*;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BankTransferServiceImpl implements BankTransferService {
    private final TuitionLedgerService ledger;
    private final TuitionIntentRepository intents;
    private final TuitionBankEventRepository events;
    private final PaymentIntentLedgerRepository repository;
    private final PaymentAllocationRepository allocations;
    private final PaymentPostingService posting;
    private final TuitionPaymentMapper mapper;

    @Override
    @Transactional
    public PaymentReceiptResponse receive(VerifiedBankTransfer transfer) {
        BankTransferPolicy.money(transfer.amount(), "receivedAmount");
        String reference = transfer.reference() == null ? "" : transfer.reference().trim();
        if (reference.isEmpty() || reference.length() > 120)
            throw BankTransferPolicy.invalid(
                    "reference", "Mã tham chiếu ngân hàng phải có từ 1 đến 120 ký tự.");
        var intent =
                transfer.orderCode() == null
                        ? null
                        : intents.findByOrderCode(transfer.orderCode()).orElse(null);
        Invoice invoice = intent == null ? null : ledger.lock(intent.getInvoice().getId());
        if (intent != null && !transfer.simulated() && intent.isSimulated())
            throw BankTransferPolicy.invalid(
                    "reference", "Không được ghi giao dịch ngân hàng thật vào QR mô phỏng.");
        if (transfer.simulated() && (intent == null || !intent.isSimulated()))
            throw BankTransferPolicy.invalid(
                    "reference", "Chỉ được mô phỏng giao dịch cho QR mô phỏng.");
        var existing = events.findByReference(reference);
        if (existing.isPresent()) {
            var old = existing.get();
            if (old.getReceivedAmount().compareTo(transfer.amount()) == 0) {
                if (old.getTransferContent() == null)
                    old.setTransferContent(clip(transfer.content(), 500));
                if (old.getSenderName() == null)
                    old.setSenderName(clip(transfer.senderName(), 150));
                if (old.getSenderAccount() == null)
                    old.setSenderAccount(clip(transfer.senderAccount(), 100));
                if (old.getSenderBank() == null)
                    old.setSenderBank(clip(transfer.senderBank(), 100));
                if (old.getTransferredAt() == null) old.setTransferredAt(transfer.transferredAt());
            }
            if (!Objects.equals(old.getGatewayOrderCode(), transfer.orderCode())
                    || old.getReceivedAmount().compareTo(transfer.amount()) != 0)
                throw new TuitionOperationException(
                        HttpStatus.CONFLICT,
                        "BANK_REFERENCE_CONFLICT",
                        "Mã giao dịch ngân hàng đã được lưu với mã đơn hoặc số tiền khác. Không"
                                + " được ghi nhận lại.");
            return old.getPayment() == null
                    ? null
                    : mapper.toPaymentReceiptResponse(old.getPayment(), null, null);
        }
        var event = new TuitionBankEvent();
        event.setIntent(intent);
        event.setGatewayOrderCode(transfer.orderCode());
        event.setReference(reference);
        event.setReceivedAmount(transfer.amount());
        event.setSource(transfer.simulated() ? "SIMULATION" : "GATEWAY");
        event.setTransferContent(clip(transfer.content(), 500));
        event.setSenderName(clip(transfer.senderName(), 150));
        event.setSenderAccount(clip(transfer.senderAccount(), 100));
        event.setSenderBank(clip(transfer.senderBank(), 100));
        event.setTransferredAt(transfer.transferredAt());
        event.setStatus(BankEventStatus.RECONCILE.code());
        events.saveAndFlush(event);
        if (intent == null) {
            retainAnomaly(
                    event,
                    BankTransferReason.UNKNOWN_ORDER,
                    "Chưa xác định được hóa đơn từ nội dung/mã thanh toán. Cần kiểm tra kỹ thuật"
                        + " trên sao kê; không tự ghép sang hóa đơn khác.");
            return null;
        }
        var plan = ledger.ensure(invoice);
        TuitionInstallment due =
                intent.getInstallmentId() == null
                        ? repository.firstUnpaid(plan.getId()).orElse(null)
                        : repository.installment(intent.getInstallmentId()).orElse(null);
        if (due == null || !due.getPlan().getId().equals(plan.getId())) {
            retainAnomaly(
                    event,
                    BankTransferReason.ALREADY_SETTLED,
                    "Không còn kỳ đóng phù hợp với yêu cầu chuyển khoản này.");
            return null;
        }
        BigDecimal remaining =
                due.getAmountDue().subtract(allocations.paid(due.getId())).max(BigDecimal.ZERO);
        if (remaining.compareTo(ledger.balance(invoice)) > 0) {
            retainAnomaly(
                    event,
                    BankTransferReason.INVOICE_UNAVAILABLE,
                    "Số tiền còn thiếu của kỳ vượt tổng công nợ hóa đơn. Cần kiểm tra lịch"
                            + " đóng trước khi phân bổ.");
            return null;
        }
        if (remaining.signum() == 0) {
            retainAnomaly(
                    event,
                    BankTransferReason.ALREADY_SETTLED,
                    "Kỳ học phí của QR này đã đóng đủ. Khoản nhận thêm không tự động ghi nhận; cần"
                        + " xác minh và hoàn trả.");
            return null;
        }
        if (!List.of("CREATING", "PENDING", "UNCERTAIN", "PARTIALLY_PAID")
                        .contains(intent.getStatus())
                || intent.getPlanRevision() != plan.getRevision()) {
            retainAnomaly(
                    event,
                    BankTransferReason.STALE_PAYMENT_REQUEST,
                    "QR đã hủy, đã xử lý hoặc lịch đóng/công nợ đã thay đổi. Không tự phân bổ sang"
                            + " kỳ khác.");
            return null;
        }
        try {
            BankTransferPolicy.open(invoice, plan);
        } catch (TuitionOperationException ex) {
            retainAnomaly(event, BankTransferReason.INVOICE_UNAVAILABLE, ex.getMessage());
            return null;
        }
        var first = repository.firstUnpaid(plan.getId()).orElse(null);
        if (first == null || !first.getId().equals(due.getId())) {
            retainAnomaly(
                    event,
                    BankTransferReason.STALE_PAYMENT_REQUEST,
                    "QR không thuộc kỳ còn thiếu sớm nhất của hóa đơn.");
            return null;
        }
        if (events.pendingInvoiceHolds(invoice.getId(), event.getId()) > 0) {
            retainAnomaly(
                    event,
                    BankTransferReason.INVOICE_UNAVAILABLE,
                    "Hóa đơn đang có giao dịch khác chưa xác minh. Khoản nhận thêm được lưu"
                            + " để kiểm tra kỹ thuật, không tự thay đổi công nợ.");
            return null;
        }
        intent.setInstallmentId(due.getId());
        if (transfer.amount().compareTo(intent.getExpectedAmount()) != 0
                || transfer.amount().compareTo(remaining) != 0) {
            retainAnomaly(
                    event,
                    BankTransferReason.AMOUNT_MISMATCH,
                    "Số tiền nhận không khớp yêu cầu VietQR Pro. Chỉ lưu dấu vết; không ghi học phí"
                        + " hoặc tự phân bổ sang kỳ khác.");
            return null;
        }
        var payment =
                posting.post(
                        invoice,
                        plan,
                        transfer.amount(),
                        PaymentMethod.VIET_QR,
                        null,
                        "BANK-" + event.getId(),
                        reference.length() <= 100 ? reference : "BANK-EVENT-" + event.getId(),
                        null);
        event.setPayment(payment);
        event.setResolvedInvoice(invoice);
        event.setAllocatedAmount(transfer.amount());
        event.setRefundedAmount(BigDecimal.ZERO);
        event.setStatus(BankEventStatus.POSTED.code());
        events.saveAndFlush(event);
        refreshProgress(invoice, plan, due);
        return mapper.toPaymentReceiptResponse(payment, null, null);
    }

    private void retainAnomaly(TuitionBankEvent event, BankTransferReason reason, String note) {
        event.setReason(reason);
        event.setNote(note);
        if (event.getIntent() != null)
            event.getIntent().setStatus(PaymentIntentStatus.RECONCILE.code());
    }

    private void refreshProgress(Invoice invoice, TuitionPlan plan, TuitionInstallment due) {
        var remaining =
                due.getAmountDue().subtract(allocations.paid(due.getId())).max(BigDecimal.ZERO);
        var related =
                new java.util.ArrayList<>(repository.boundIntents(invoice.getId(), due.getId()));
        for (var old : repository.unboundIntents(invoice.getId()))
            if (old.getPlanRevision() == plan.getRevision() - 1) {
                old.setInstallmentId(due.getId());
                related.add(old);
            }
        for (var intent : related) {
            if (events.existsByIntentIdAndStatus(intent.getId(), BankEventStatus.RECONCILE.code()))
                continue;
            if (remaining.signum() == 0) intent.setStatus(PaymentIntentStatus.PAID.code());
            else if (intent.getStatus().equals(PaymentIntentStatus.RECONCILE.code()))
                intent.setStatus(PaymentIntentStatus.PENDING.code());
            intent.setPlanRevision(plan.getRevision());
        }
    }

    private String clip(String s, int length) {
        return s == null ? null : s.substring(0, Math.min(s.length(), length));
    }
}
