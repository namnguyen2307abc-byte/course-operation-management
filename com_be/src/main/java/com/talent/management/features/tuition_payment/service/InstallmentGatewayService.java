package com.talent.management.features.tuition_payment.service;

import static com.talent.management.features.tuition_payment.service.TuitionPlanService.conflict;

import static org.springframework.http.HttpStatus.*;

import com.talent.management.features.tuition_payment.dto.request.InstallmentRequests.ManualBankConfirmation;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.*;
import com.talent.management.features.tuition_payment.dto.response.PayOSPaymentLinkResponse;
import com.talent.management.features.tuition_payment.dto.response.PaymentReceiptResponse;
import com.talent.management.features.tuition_payment.gateway.PayOSGateway;
import com.talent.management.features.tuition_payment.mapper.TuitionPaymentMapper;
import com.talent.management.features.tuition_payment.repository.*;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.service.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InstallmentGatewayService {
    private final TuitionPlanService ledger;
    private final PaymentPostingService posting;
    private final TuitionIntentRepository intents;
    private final TuitionBankEventRepository events;
    private final CurrentUserService current;
    private final PayOSGateway gateway;
    private final TuitionPaymentMapper mapper;
    private final TransactionTemplate tx;

    @Value("${tuition.simulation.enabled:false}")
    private boolean simulation;

    public IntentView create(Long invoiceId, BigDecimal amount) {
        return createAs(invoiceId, amount, current.getCurrentUser(), false);
    }

    // Only the scheduled reminder flow uses this entry point, never a public controller.
    public IntentView createForReminder(Long invoiceId) {
        User parent =
                tx.execute(
                        status -> {
                            User recipient = ledger.lock(invoiceId).getStudent().getParent();
                            if (recipient != null) org.hibernate.Hibernate.initialize(recipient);
                            return recipient;
                        });
        if (parent == null) throw conflict("Không có phụ huynh nhận thông báo.");
        return createAs(invoiceId, null, parent, true);
    }

    private IntentView createAs(
            Long invoiceId, BigDecimal requestedAmount, User actor, boolean reminder) {
        releaseMissingRequests(invoiceId, actor);
        TuitionPaymentIntent intent =
                tx.execute(
                        status -> {
                            Invoice invoice = ledger.lock(invoiceId);
                            if (actor.getRole() == Role.PARENT
                                    && (invoice.getStudent().getParent() == null
                                            || !invoice.getStudent()
                                                    .getParent()
                                                    .getId()
                                                    .equals(actor.getId())))
                                throw new ResponseStatusException(
                                        NOT_FOUND, "Không tìm thấy học phí của con bạn.");
                            TuitionPlan plan = ledger.ensure(invoice);
                            BigDecimal amount = requestedAmount;
                            if (reminder) {
                                InvoiceView view = ledger.map(invoice, plan);
                                Due due =
                                        view.installments().stream()
                                                .filter(d -> d.remaining().signum() > 0)
                                                .findFirst()
                                                .orElseThrow(() -> conflict("Đã đóng đủ."));
                                if (!view.confirmed()
                                        || due.date().isAfter(TuitionPlanService.today()))
                                    throw conflict("Chưa đến hạn phát hành QR nhắc học phí.");
                                amount = due.remaining();
                            }
                            ledger.requireCollectible(invoice, plan, amount);
                            if (amount.signum() == 0)
                                throw conflict("Hóa đơn miễn phí không cần QR.");
                            List<TuitionPaymentIntent> active =
                                    new ArrayList<>(
                                            intents.findByInvoiceIdAndStatus(
                                                    invoiceId, "CREATING"));
                            active.addAll(intents.findByInvoiceIdAndStatus(invoiceId, "UNCERTAIN"));
                            active.addAll(intents.findByInvoiceIdAndStatus(invoiceId, "PENDING"));
                            for (TuitionPaymentIntent old : active) {
                                if (old.getStatus().equals("PENDING")
                                        && old.getExpectedAmount().compareTo(amount) == 0
                                        && old.getPlanRevision() == plan.getRevision()) return old;
                                throw conflict(
                                        "Đang có QR chờ thanh toán. Vui lòng mở QR đang chờ để thanh toán đúng số tiền.");
                            }
                            TuitionPaymentIntent created = new TuitionPaymentIntent();
                            created.setInvoice(invoice);
                            created.setExpectedAmount(amount);
                            created.setPlanRevision(plan.getRevision());
                            created.setInitiatedBy(actor);
                            created.setOrderCode(
                                    Math.floorMod(
                                                    UUID.randomUUID().getMostSignificantBits(),
                                                    8_000_000_000_000_000L)
                                            + 1);
                            return intents.saveAndFlush(created);
                        });
        if (!intent.getStatus().equals("CREATING")) return map(intent);
        try {
            // Detached snapshots prevent holding the database lock during a gateway call.
            Invoice snapshot = new Invoice();
            snapshot.setId(invoiceId);
            snapshot.setInvoiceCode("HP" + intent.getOrderCode());
            snapshot.setFinalAmount(intent.getExpectedAmount());
            boolean parent = actor.getRole() == Role.PARENT;
            PayOSPaymentLinkResponse link =
                    gateway.createPaymentLink(
                            snapshot, intent.getExpectedAmount(), intent.getOrderCode(), parent);
            return tx.execute(
                    status -> {
                        ledger.lock(invoiceId);
                        TuitionPaymentIntent saved = intents.findById(intent.getId()).orElseThrow();
                        saved.setCheckoutUrl(link.getCheckoutUrl());
                        saved.setQrCode(link.getQrCode());
                        saved.setSimulated(link.isSimulated());
                        if (saved.getStatus().equals("CREATING")) saved.setStatus("PENDING");
                        return map(saved);
                    });
        } catch (RuntimeException ex) {
            tx.executeWithoutResult(
                    status -> {
                        ledger.lock(invoiceId);
                        TuitionPaymentIntent saved = intents.findById(intent.getId()).orElseThrow();
                        // Unknown network result must remain visible for reconciliation, not
                        // silently simulated.
                        if (saved.getStatus().equals("CREATING")) saved.setStatus("UNCERTAIN");
                    });
            throw ex;
        }
    }

    public IntentView status(Long id) {
        tx.executeWithoutResult(status -> {
            TuitionPaymentIntent found = intents.findById(id).orElseThrow();
            ledger.checkParent(found.getInvoice());
        });
        refreshUncertain(id);
        return tx.execute(
                status -> {
                    TuitionPaymentIntent intent =
                            intents.findById(id)
                                    .orElseThrow(
                                            () ->
                                                    new ResponseStatusException(
                                                            NOT_FOUND, "Không tìm thấy yêu cầu."));
                    ledger.checkParent(intent.getInvoice());
                    return map(intent);
                });
    }

    private void releaseMissingRequests(Long invoiceId, User actor) {
        List<Long> uncertain = tx.execute(status -> {
            Invoice invoice = ledger.lock(invoiceId);
            if (actor.getRole() == Role.PARENT && (invoice.getStudent().getParent() == null
                    || !invoice.getStudent().getParent().getId().equals(actor.getId())))
                throw new ResponseStatusException(NOT_FOUND, "Không tìm thấy học phí của con bạn.");
            return intents.findByInvoiceIdAndStatus(invoiceId, "UNCERTAIN").stream()
                    .map(TuitionPaymentIntent::getId).toList();
        });
        for (Long id : uncertain) refreshUncertain(id);
    }

    private void refreshUncertain(Long id) {
        TuitionPaymentIntent snapshot = tx.execute(status -> intents.findById(id).orElseThrow());
        if ("UNCERTAIN".equals(snapshot.getStatus()) && !snapshot.isSimulated()
                && gateway.isMissingOrClosed(snapshot.getOrderCode())) {
            tx.executeWithoutResult(status -> {
                ledger.lock(snapshot.getInvoice().getId());
                TuitionPaymentIntent saved = intents.findById(id).orElseThrow();
                if ("UNCERTAIN".equals(saved.getStatus())) saved.setStatus("FAILED");
            });
        }
    }

    /** Changing to cash closes unpaid links at the gateway first; the cash writer rechecks under lock. */
    public void prepareCashPayment(Long invoiceId) {
        if (!List.of(Role.STAFF, Role.ADMIN, Role.BRANCH_MANAGER).contains(current.getCurrentUser().getRole()))
            throw new ResponseStatusException(FORBIDDEN, "Chỉ nhân viên được thu tiền mặt.");
        List<Long> active = tx.execute(status -> {
            ledger.lock(invoiceId);
            if (!intents.findByInvoiceIdAndStatus(invoiceId, "CREATING").isEmpty())
                throw conflict("QR đang được tạo. Vui lòng đợi hoàn tất trước khi thu tiền mặt.");
            List<TuitionPaymentIntent> pending = new ArrayList<>(intents.findByInvoiceIdAndStatus(invoiceId, "PENDING"));
            pending.addAll(intents.findByInvoiceIdAndStatus(invoiceId, "UNCERTAIN"));
            return pending.stream().map(TuitionPaymentIntent::getId).toList();
        });
        for (Long id : active) cancel(id);
    }

    public void cancel(Long id) {
        TuitionPaymentIntent snapshot =
                tx.execute(
                        status -> {
                            TuitionPaymentIntent intent = intents.findById(id).orElseThrow();
                            ledger.checkParent(intent.getInvoice());
                            return intent;
                        });
        if (!snapshot.isSimulated()) gateway.cancelPayment(snapshot.getOrderCode());
        tx.executeWithoutResult(
                status -> {
                    ledger.lock(snapshot.getInvoice().getId());
                    TuitionPaymentIntent intent = intents.findById(id).orElseThrow();
                    if (!List.of("PENDING", "CREATING", "UNCERTAIN").contains(intent.getStatus()))
                        throw conflict("Yêu cầu đã được xử lý.");
                    intent.setStatus("CANCELLED");
                });
    }

    public PaymentReceiptResponse webhook(Map<String, Object> body) {
        if (!gateway.verifyWebhookSignature(body))
            throw new ResponseStatusException(BAD_REQUEST, "Chữ ký PayOS không hợp lệ.");
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
        return receive(code, amount, ref, false);
    }

    public PaymentReceiptResponse simulate(Long id) {
        if (!simulation) throw new ResponseStatusException(FORBIDDEN, "Mô phỏng chưa được bật.");
        TuitionPaymentIntent intent = tx.execute(status -> intents.findById(id).orElseThrow());
        if (!intent.isSimulated()) throw conflict("Đây là yêu cầu thanh toán thật.");
        return receive(
                intent.getOrderCode(),
                intent.getExpectedAmount(),
                "DEMO-" + intent.getOrderCode(),
                true);
    }

    public PaymentReceiptResponse confirmBank(Long id, ManualBankConfirmation input) {
        User actor = current.getCurrentUser();
        if (!List.of(Role.STAFF, Role.ADMIN, Role.BRANCH_MANAGER).contains(actor.getRole()))
            throw new ResponseStatusException(
                    FORBIDDEN, "Chỉ nhân viên được xác nhận tiền ngân hàng thực nhận.");
        return tx.execute(
                status -> {
                    Long invoiceId = intents.findInvoiceId(id).orElseThrow();
                    Invoice invoice = ledger.lock(invoiceId);
                    TuitionPaymentIntent intent = intents.findById(id).orElseThrow();
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
                                            && intent.getStatus().equals("RECONCILE")))
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
                    if (!List.of("PENDING", "UNCERTAIN").contains(intent.getStatus())
                            && !(actor.getRole() == Role.ADMIN
                                    && intent.getStatus().equals("RECONCILE")))
                        throw conflict("Yêu cầu đã ghi nhận hoặc không còn nhận thanh toán.");
                    TuitionPlan plan = ledger.ensure(invoice);
                    ledger.requireCollectible(invoice, plan, input.amount());
                    Payment payment =
                            posting.post(
                                    invoice,
                                    plan,
                                    input.amount(),
                                    com.talent.management.shared.enums.PaymentMethod.VIET_QR,
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
                    intent.setStatus("PAID");
                    return mapper.toPaymentReceiptResponse(payment, null, null);
                });
    }

    private PaymentReceiptResponse receive(
            long code, BigDecimal amount, String reference, boolean simulated) {
        TuitionPaymentIntent snapshot =
                tx.execute(status -> intents.findByOrderCode(code).orElse(null));
        if (snapshot == null) {
            return tx.execute(
                    status -> {
                        Optional<TuitionBankEvent> existing = events.findByReference(reference);
                        if (existing.isPresent()) {
                            if (!Objects.equals(existing.get().getGatewayOrderCode(), code)
                                    || existing.get().getReceivedAmount().compareTo(amount) != 0) {
                                throw conflict("Tham chiếu đã có với nội dung khác.");
                            }
                            return null;
                        }
                        ledger.validateMoney(amount);
                        TuitionBankEvent unmatched = new TuitionBankEvent();
                        unmatched.setGatewayOrderCode(code);
                        unmatched.setReference(reference);
                        unmatched.setReceivedAmount(amount);
                        unmatched.setStatus("RECONCILE");
                        unmatched.setNote(
                                "Webhook đã xác thực nhưng chưa xác định được hóa đơn cho mã đơn "
                                        + code);
                        events.saveAndFlush(unmatched);
                        return null;
                    });
        }
        return tx.execute(
                status -> {
                    Invoice invoice = ledger.lock(snapshot.getInvoice().getId());
                    TuitionPaymentIntent intent = intents.findById(snapshot.getId()).orElseThrow();
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
                    ledger.validateMoney(amount);
                    TuitionBankEvent event = new TuitionBankEvent();
                    event.setIntent(intent);
                    event.setGatewayOrderCode(code);
                    event.setReference(reference);
                    event.setReceivedAmount(amount);
                    event.setStatus("RECONCILE");
                    events.saveAndFlush(event);
                    TuitionPlan plan = ledger.ensure(invoice);
                    if (amount.signum() == 0
                            || amount.compareTo(intent.getExpectedAmount()) != 0
                            || intent.getStatus().equals("PAID")
                            || intent.getStatus().equals("RECONCILE")
                            || invoice.getStatus()
                                    == com.talent.management.shared.enums.InvoiceStatus.CANCELLED
                            || plan.isReviewRequired()
                            || amount.compareTo(ledger.balance(invoice)) > 0) {
                        intent.setStatus("RECONCILE");
                        event.setNote(
                                "Tiền đã nhận cần đối chiếu số tiền, số dư hoặc trạng thái yêu"
                                        + " cầu.");
                        return null;
                    }
                    try {
                        ledger.requireCollectible(invoice, plan, amount);
                    } catch (ResponseStatusException ex) {
                        intent.setStatus("RECONCILE");
                        event.setNote(ex.getReason());
                        return null;
                    }
                    Payment payment =
                            posting.post(
                                    invoice,
                                    plan,
                                    amount,
                                    com.talent.management.shared.enums.PaymentMethod.VIET_QR,
                                    null,
                                    "QR-" + intent.getOrderCode(),
                                    reference,
                                    amount);
                    event.setPayment(payment);
                    event.setStatus("POSTED");
                    intent.setStatus("PAID");
                    return mapper.toPaymentReceiptResponse(payment, null, null);
                });
    }

    private IntentView map(TuitionPaymentIntent intent) {
        return new IntentView(
                intent.getId(),
                intent.getOrderCode(),
                intent.getExpectedAmount(),
                intent.getStatus(),
                intent.getCheckoutUrl(),
                intent.getQrCode(),
                intent.isSimulated());
    }
}
