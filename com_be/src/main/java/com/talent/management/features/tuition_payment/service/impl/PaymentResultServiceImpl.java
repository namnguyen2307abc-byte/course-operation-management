package com.talent.management.features.tuition_payment.service.impl;

import static com.talent.management.features.tuition_payment.exception.TuitionOperationException.conflict;
import static com.talent.management.shared.enums.InvoiceStatus.CANCELLED;
import static com.talent.management.shared.enums.PaymentMethod.VIET_QR;

import static org.springframework.http.HttpStatus.*;

import com.talent.management.features.tuition_payment.dto.request.ConfirmBankPaymentRequest;
import com.talent.management.features.tuition_payment.dto.response.PaymentReceiptResponse;
import com.talent.management.features.tuition_payment.enums.PaymentIntentStatus;
import com.talent.management.features.tuition_payment.exception.TuitionOperationException;
import com.talent.management.features.tuition_payment.gateway.PayOSGateway;
import com.talent.management.features.tuition_payment.mapper.TuitionPaymentMapper;
import com.talent.management.features.tuition_payment.repository.TuitionBankEventRepository;
import com.talent.management.features.tuition_payment.repository.TuitionIntentRepository;
import com.talent.management.features.tuition_payment.service.PaymentPostingService;
import com.talent.management.features.tuition_payment.service.PaymentResultService;
import com.talent.management.features.tuition_payment.service.TuitionLedgerService;
import com.talent.management.shared.entity.Invoice;
import com.talent.management.shared.entity.Payment;
import com.talent.management.shared.entity.TuitionBankEvent;
import com.talent.management.shared.entity.TuitionPaymentIntent;
import com.talent.management.shared.entity.TuitionPlan;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.service.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentResultServiceImpl implements PaymentResultService {
    private final TuitionLedgerService tuitionLedgerService;
    private final PaymentPostingService paymentPostingService;
    private final TuitionIntentRepository intents;
    private final TuitionBankEventRepository events;
    private final CurrentUserService currentUserService;
    private final PayOSGateway payOSGateway;
    private final TuitionPaymentMapper mapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public PaymentReceiptResponse webhook(Map<String, Object> body) {
        if (!payOSGateway.verifyWebhookSignature(body))
            throw new TuitionOperationException(
                    BAD_REQUEST, "PAYMENT_SIGNATURE_INVALID", "Chữ ký PayOS không hợp lệ.");
        if (!(body.get("data") instanceof Map<?, ?> data))
            throw conflict("Thiếu dữ liệu giao dịch.");
        if (!"00".equals(String.valueOf(data.get("code")))) return null;
        if (!"00".equals(String.valueOf(body.get("code")))
                || !Boolean.TRUE.equals(body.get("success"))) return null;
        // PayOS sends this signed sample when confirming the webhook URL.
        // A connectivity probe must never create a payment or reconciliation item.
        if ("123".equals(String.valueOf(data.get("orderCode")))
                && "124c33293c43417ab7879e14c8d9eb18".equals(data.get("paymentLinkId"))
                && "TF230204212323".equals(data.get("reference"))) return null;
        long code;
        BigDecimal amount;
        try {
            code = Long.parseLong(String.valueOf(data.get("orderCode")));
            amount = new BigDecimal(String.valueOf(data.get("amount")));
        } catch (RuntimeException ex) {
            throw conflict("Số tiền hoặc mã đơn không hợp lệ.");
        }
        String ref = String.valueOf(data.get("reference"));
        if (ref.isBlank() || ref.equals("null") || ref.length() > 120)
            throw conflict("Thiếu mã tham chiếu ngân hàng.");
        return applyVerifiedTransfer(code, amount, ref, false);
    }

    @Override
    public PaymentReceiptResponse confirmBank(Long id, ConfirmBankPaymentRequest input) {
        User actor = currentUserService.getCurrentUser();
        if (!List.of(Role.STAFF, Role.ADMIN, Role.BRANCH_MANAGER).contains(actor.getRole()))
            throw new TuitionOperationException(
                    FORBIDDEN, "Chỉ nhân viên được xác nhận tiền ngân hàng thực nhận.");
        return transactionTemplate.execute(
                status -> {
                    Long invoiceId =
                            intents.findInvoiceId(id)
                                    .orElseThrow(TuitionOperationException::notFound);
                    Invoice invoice = tuitionLedgerService.lock(invoiceId);
                    TuitionPaymentIntent intent =
                            intents.findById(id).orElseThrow(TuitionOperationException::notFound);
                    String reference = input.reference() == null ? "" : input.reference().trim();
                    String reason = input.reason() == null ? "" : input.reason().trim();
                    if (!input.bankVerified()
                            || reference.isBlank()
                            || reference.length() > 120
                            || reason.isBlank()
                            || reason.length() > 400
                            || input.amount() == null
                            || (input.amount().compareTo(intent.getExpectedAmount()) != 0
                                    && !(actor.getRole() == Role.ADMIN
                                            && intent.getStatus()
                                                    .equals(PaymentIntentStatus.RECONCILE.code())))
                            || intent.isSimulated())
                        throw conflict(
                                "Cần kiểm tra ngân hàng thực nhận, nhập đúng số tiền QR, mã giao"
                                        + " dịch và lý do xác nhận thủ công.");
                    Optional<TuitionBankEvent> duplicate = events.findByReference(reference);
                    if (duplicate.isPresent()) {
                        TuitionBankEvent old = duplicate.get();
                        if (old.getIntent() != null
                                && old.getIntent().getId().equals(id)
                                && old.getReceivedAmount().compareTo(input.amount()) == 0
                                && old.getPayment() != null)
                            return mapper.toPaymentReceiptResponse(old.getPayment(), null, null);
                        if (!(actor.getRole() == Role.ADMIN
                                && old.getIntent() != null
                                && old.getIntent().getId().equals(id)
                                && old.getReceivedAmount().compareTo(input.amount()) == 0
                                && old.getPayment() == null))
                            throw conflict(
                                    "Mã ngân hàng đã dùng với nội dung khác. Cần kiểm tra lại giao"
                                            + " dịch thực nhận.");
                    }
                    if (!List.of(
                                            PaymentIntentStatus.PENDING.code(),
                                            PaymentIntentStatus.UNCERTAIN.code())
                                    .contains(intent.getStatus())
                            && !(actor.getRole() == Role.ADMIN
                                    && intent.getStatus()
                                            .equals(PaymentIntentStatus.RECONCILE.code())))
                        throw conflict("Yêu cầu đã ghi nhận hoặc không còn nhận thanh toán.");
                    TuitionPlan plan = tuitionLedgerService.ensure(invoice);
                    tuitionLedgerService.requireCollectible(invoice, plan, input.amount());
                    Payment payment =
                            paymentPostingService.post(
                                    invoice,
                                    plan,
                                    input.amount(),
                                    VIET_QR,
                                    actor,
                                    "QR-" + intent.getOrderCode(),
                                    reference,
                                    input.amount());
                    TuitionBankEvent event = duplicate.orElseGet(TuitionBankEvent::new);
                    event.setIntent(intent);
                    event.setGatewayOrderCode(intent.getOrderCode());
                    event.setReference(reference);
                    event.setReceivedAmount(input.amount());
                    event.setStatus("POSTED");
                    event.setPayment(payment);
                    event.setReviewedBy(actor);
                    event.setReviewedAt(java.time.LocalDateTime.now());
                    event.setNote("Xác nhận ngân hàng thủ công: " + reason);
                    events.saveAndFlush(event);
                    intent.setStatus(PaymentIntentStatus.PAID.code());
                    return mapper.toPaymentReceiptResponse(payment, null, null);
                });
    }

    @Override
    public PaymentReceiptResponse applyVerifiedTransfer(
            long code, BigDecimal amount, String reference, boolean simulated) {
        TuitionPaymentIntent snapshot =
                transactionTemplate.execute(status -> intents.findByOrderCode(code).orElse(null));
        if (snapshot == null) {
            return transactionTemplate.execute(
                    status -> {
                        Optional<TuitionBankEvent> existing = events.findByReference(reference);
                        if (existing.isPresent()) {
                            if (!Objects.equals(existing.get().getGatewayOrderCode(), code)
                                    || existing.get().getReceivedAmount().compareTo(amount) != 0) {
                                throw conflict("Tham chiếu đã có với nội dung khác.");
                            }
                            return null;
                        }
                        tuitionLedgerService.validateMoney(amount);
                        TuitionBankEvent unmatched = new TuitionBankEvent();
                        unmatched.setGatewayOrderCode(code);
                        unmatched.setReference(reference);
                        unmatched.setReceivedAmount(amount);
                        unmatched.setStatus(PaymentIntentStatus.RECONCILE.code());
                        unmatched.setNote(
                                "Webhook đã xác thực nhưng chưa xác định được hóa đơn cho mã đơn "
                                        + code);
                        events.saveAndFlush(unmatched);
                        return null;
                    });
        }
        return transactionTemplate.execute(
                status -> {
                    Invoice invoice = tuitionLedgerService.lock(snapshot.getInvoice().getId());
                    TuitionPaymentIntent intent =
                            intents.findById(snapshot.getId())
                                    .orElseThrow(TuitionOperationException::notFound);
                    if (!simulated && intent.isSimulated())
                        throw conflict("Không nhận webhook thật cho QR mô phỏng.");
                    Optional<TuitionBankEvent> duplicate = events.findByReference(reference);
                    if (duplicate.isPresent()) {
                        TuitionBankEvent old = duplicate.get();
                        if (old.getIntent() == null
                                || !old.getIntent().getId().equals(intent.getId())
                                || old.getReceivedAmount().compareTo(amount) != 0)
                            throw conflict("Tham chiếu đã có với nội dung khác.");
                        return old.getPayment() == null
                                ? null
                                : mapper.toPaymentReceiptResponse(old.getPayment(), null, null);
                    }
                    tuitionLedgerService.validateMoney(amount);
                    TuitionBankEvent event = new TuitionBankEvent();
                    event.setIntent(intent);
                    event.setGatewayOrderCode(code);
                    event.setReference(reference);
                    event.setReceivedAmount(amount);
                    event.setStatus(PaymentIntentStatus.RECONCILE.code());
                    events.saveAndFlush(event);
                    TuitionPlan plan = tuitionLedgerService.ensure(invoice);
                    if (amount.signum() == 0
                            || amount.compareTo(intent.getExpectedAmount()) != 0
                            || intent.getStatus().equals(PaymentIntentStatus.PAID.code())
                            || intent.getStatus().equals(PaymentIntentStatus.RECONCILE.code())
                            || intent.getStatus().equals(PaymentIntentStatus.CANCELLED.code())
                            || intent.getStatus().equals(PaymentIntentStatus.FAILED.code())
                            || intent.getPlanRevision() != plan.getRevision()
                            || invoice.getStatus() == CANCELLED
                            || plan.isReviewRequired()
                            || amount.compareTo(tuitionLedgerService.balance(invoice)) > 0) {
                        intent.setStatus(PaymentIntentStatus.RECONCILE.code());
                        event.setNote(
                                "Tiền đã nhận cần đối chiếu số tiền, số dư hoặc trạng thái yêu"
                                        + " cầu.");
                        return null;
                    }
                    try {
                        tuitionLedgerService.requireCollectible(invoice, plan, amount);
                    } catch (TuitionOperationException ex) {
                        intent.setStatus(PaymentIntentStatus.RECONCILE.code());
                        event.setNote(ex.getReason());
                        return null;
                    }
                    Payment payment =
                            paymentPostingService.post(
                                    invoice,
                                    plan,
                                    amount,
                                    VIET_QR,
                                    null,
                                    "QR-" + intent.getOrderCode(),
                                    reference,
                                    amount);
                    event.setPayment(payment);
                    event.setStatus("POSTED");
                    intent.setStatus(PaymentIntentStatus.PAID.code());
                    return mapper.toPaymentReceiptResponse(payment, null, null);
                });
    }
}
