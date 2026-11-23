package com.talent.management.features.tuition_payment.service.impl;

import static com.talent.management.features.tuition_payment.exception.TuitionOperationException.conflict;

import static org.springframework.http.HttpStatus.*;

import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.Due;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.IntentView;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.InvoiceView;
import com.talent.management.features.tuition_payment.dto.response.PayOSPaymentLinkResponse;
import com.talent.management.features.tuition_payment.dto.response.PaymentReceiptResponse;
import com.talent.management.features.tuition_payment.enums.PaymentIntentStatus;
import com.talent.management.features.tuition_payment.exception.TuitionOperationException;
import com.talent.management.features.tuition_payment.gateway.PayOSGateway;
import com.talent.management.features.tuition_payment.gateway.VerifiedBankTransfer;
import com.talent.management.features.tuition_payment.policy.CashTenderPolicy;
import com.talent.management.features.tuition_payment.repository.PaymentAllocationRepository;
import com.talent.management.features.tuition_payment.repository.PaymentIntentLedgerRepository;
import com.talent.management.features.tuition_payment.repository.TuitionIntentRepository;
import com.talent.management.features.tuition_payment.service.PaymentIntentService;
import com.talent.management.features.tuition_payment.service.PaymentResultService;
import com.talent.management.features.tuition_payment.service.TuitionLedgerService;
import com.talent.management.features.tuition_payment.service.TuitionQueryService;
import com.talent.management.features.tuition_payment.support.TuitionDates;
import com.talent.management.shared.entity.Invoice;
import com.talent.management.shared.entity.TuitionPaymentIntent;
import com.talent.management.shared.entity.TuitionPlan;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.service.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentIntentServiceImpl implements PaymentIntentService {
    private final TuitionLedgerService tuitionLedgerService;
    private final PaymentIntentLedgerRepository bankRepository;
    private final PaymentAllocationRepository bankAllocations;
    private final TuitionQueryService tuitionQueryService;
    private final PaymentResultService paymentResults;
    private final TuitionIntentRepository intents;
    private final CurrentUserService currentUserService;
    private final PayOSGateway payOSGateway;
    private final TransactionTemplate transactionTemplate;

    @Value("${tuition.simulation.enabled:false}")
    private boolean simulation;

    @Value("${tuition.qr-lifetime-seconds:0}")
    private long qrLifetimeSeconds = 0;

    @Override
    public IntentView create(Long invoiceId, BigDecimal amount) {
        return createAs(invoiceId, amount, currentUserService.getCurrentUser(), false);
    }

    // Only the scheduled reminder flow uses this entry point, never a public controller.
    @Override
    public IntentView createForReminder(Long invoiceId) {
        User parent =
                transactionTemplate.execute(
                        status -> {
                            User recipient =
                                    tuitionLedgerService.lock(invoiceId).getStudent().getParent();
                            if (recipient != null) org.hibernate.Hibernate.initialize(recipient);
                            return recipient;
                        });
        if (parent == null) throw conflict("Không có phụ huynh nhận thông báo.");
        return createAs(invoiceId, null, parent, true);
    }

    private IntentView createAs(
            Long invoiceId, BigDecimal requestedAmount, User actor, boolean reminder) {
        releaseMissingRequests(invoiceId, actor);
        if (!reminder) {
            transactionTemplate.executeWithoutResult(
                    status -> {
                        Invoice invoice = tuitionLedgerService.lock(invoiceId);
                        tuitionLedgerService.requireCurrentInstallment(
                                invoice, tuitionLedgerService.ensure(invoice), requestedAmount);
                    });
            List<Long> pending =
                    transactionTemplate.execute(
                            status ->
                                    intents
                                            .findByInvoiceIdAndStatus(
                                                    invoiceId, PaymentIntentStatus.PENDING.code())
                                            .stream()
                                            .map(TuitionPaymentIntent::getId)
                                            .toList());
            for (Long id : pending) {
                // A user starts a fresh checkout. Verify and close the previous attempt first,
                // without holding an invoice lock while calling the gateway.
                cancelIntent(id);
                String outcome =
                        transactionTemplate.execute(
                                tx ->
                                        intents.findById(id)
                                                .orElseThrow(TuitionOperationException::notFound)
                                                .getStatus());
                if (!List.of(
                                PaymentIntentStatus.CANCELLED.code(),
                                PaymentIntentStatus.FAILED.code())
                        .contains(outcome)) {
                    throw conflict(
                            "Yêu cầu trước đã nhận tiền hoặc đang xác minh. Số dư đã thay đổi; vui"
                                    + " lòng tải lại hóa đơn trước khi thanh toán phần còn thiếu.");
                }
            }
        }
        TuitionPaymentIntent intent =
                transactionTemplate.execute(
                        status -> {
                            Invoice invoice = tuitionLedgerService.lock(invoiceId);
                            if (actor.getRole() == Role.PARENT
                                    && (invoice.getStudent().getParent() == null
                                            || !invoice.getStudent()
                                                    .getParent()
                                                    .getId()
                                                    .equals(actor.getId())))
                                throw new TuitionOperationException(
                                        NOT_FOUND, "Không tìm thấy học phí của con bạn.");
                            TuitionPlan plan = tuitionLedgerService.ensure(invoice);
                            BigDecimal amount = requestedAmount;
                            if (reminder) {
                                InvoiceView view = tuitionQueryService.map(invoice, plan);
                                Due due =
                                        view.installments().stream()
                                                .filter(d -> d.remaining().signum() > 0)
                                                .findFirst()
                                                .orElseThrow(() -> conflict("Đã đóng đủ."));
                                if (!view.confirmed() || due.date().isAfter(TuitionDates.today()))
                                    throw conflict("Chưa đến hạn phát hành QR nhắc học phí.");
                                amount = due.remaining();
                            }
                            tuitionLedgerService.requireCurrentInstallment(invoice, plan, amount);
                            if (amount.signum() == 0)
                                throw conflict("Hóa đơn miễn phí không cần QR.");
                            List<TuitionPaymentIntent> active =
                                    new ArrayList<>(
                                            intents.findByInvoiceIdAndStatus(
                                                    invoiceId,
                                                    PaymentIntentStatus.CREATING.code()));
                            active.addAll(
                                    intents.findByInvoiceIdAndStatus(
                                            invoiceId, PaymentIntentStatus.UNCERTAIN.code()));
                            active.addAll(
                                    intents.findByInvoiceIdAndStatus(
                                            invoiceId, PaymentIntentStatus.PENDING.code()));
                            for (TuitionPaymentIntent old : active) {
                                if (old.getStatus().equals(PaymentIntentStatus.PENDING.code())
                                        && old.getExpectedAmount().compareTo(amount) == 0
                                        && old.getPlanRevision() == plan.getRevision()) return old;
                                throw conflict(
                                        "Yêu cầu QR trước chưa rõ kết quả. Vui lòng đợi hoặc xác"
                                                + " minh ngân hàng trước khi tạo mã mới.");
                            }
                            TuitionPaymentIntent created = new TuitionPaymentIntent();
                            created.setInvoice(invoice);
                            created.setInstallmentId(
                                    bankRepository
                                            .firstUnpaid(plan.getId())
                                            .orElseThrow(
                                                    () ->
                                                            conflict(
                                                                    "Không còn kỳ học phí nào chưa"
                                                                            + " đóng."))
                                            .getId());
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
        if (!intent.getStatus().equals(PaymentIntentStatus.CREATING.code())) return map(intent);
        try {
            // Detached snapshots prevent holding the database lock during a payOSGateway call.
            Invoice snapshot = new Invoice();
            snapshot.setId(invoiceId);
            snapshot.setInvoiceCode("HP" + intent.getOrderCode());
            snapshot.setFinalAmount(intent.getExpectedAmount());
            boolean parent = actor.getRole() == Role.PARENT;
            PayOSPaymentLinkResponse link =
                    payOSGateway.createPaymentLink(
                            snapshot, intent.getExpectedAmount(), intent.getOrderCode(), parent);
            IntentView result =
                    transactionTemplate.execute(
                            status -> {
                                tuitionLedgerService.lock(invoiceId);
                                TuitionPaymentIntent saved =
                                        intents.findById(intent.getId())
                                                .orElseThrow(TuitionOperationException::notFound);
                                saved.setCheckoutUrl(link.getCheckoutUrl());
                                saved.setQrCode(link.getQrCode());
                                saved.setSimulated(link.isSimulated());
                                if (saved.getStatus().equals(PaymentIntentStatus.CREATING.code()))
                                    saved.setStatus(PaymentIntentStatus.PENDING.code());
                                return map(saved);
                            });
            // A page-close request may arrive before the payOSGateway finishes creating the link.
            if (PaymentIntentStatus.CANCELLED.code().equals(result.status())
                    && !link.isSimulated()) {
                try {
                    payOSGateway.cancelPayment(intent.getOrderCode());
                } catch (RuntimeException failure) {
                    transactionTemplate.executeWithoutResult(
                            status -> {
                                tuitionLedgerService.lock(invoiceId);
                                TuitionPaymentIntent saved =
                                        intents.findById(intent.getId())
                                                .orElseThrow(TuitionOperationException::notFound);
                                if (PaymentIntentStatus.CANCELLED.code().equals(saved.getStatus()))
                                    saved.setStatus(PaymentIntentStatus.UNCERTAIN.code());
                            });
                    throw failure;
                }
            }
            return result;
        } catch (RuntimeException ex) {
            transactionTemplate.executeWithoutResult(
                    status -> {
                        tuitionLedgerService.lock(invoiceId);
                        TuitionPaymentIntent saved =
                                intents.findById(intent.getId())
                                        .orElseThrow(TuitionOperationException::notFound);
                        // Unknown network result must remain visible for reconciliation, not
                        // silently simulated.
                        if (saved.getStatus().equals(PaymentIntentStatus.CREATING.code()))
                            saved.setStatus(PaymentIntentStatus.UNCERTAIN.code());
                    });
            throw ex;
        }
    }

    @Override
    public IntentView status(Long id) {
        transactionTemplate.executeWithoutResult(
                status -> {
                    TuitionPaymentIntent found =
                            intents.findById(id).orElseThrow(TuitionOperationException::notFound);
                    tuitionLedgerService.checkParent(found.getInvoice());
                });
        refreshBankTransfers(id);
        refreshUncertain(id);
        return transactionTemplate.execute(
                status -> {
                    TuitionPaymentIntent intent =
                            intents.findById(id)
                                    .orElseThrow(
                                            () ->
                                                    new TuitionOperationException(
                                                            NOT_FOUND, "Không tìm thấy yêu cầu."));
                    tuitionLedgerService.checkParent(intent.getInvoice());
                    return map(intent);
                });
    }

    private void releaseMissingRequests(Long invoiceId, User actor) {
        List<Long> uncertain =
                transactionTemplate.execute(
                        status -> {
                            Invoice invoice = tuitionLedgerService.lock(invoiceId);
                            if (actor.getRole() == Role.PARENT
                                    && (invoice.getStudent().getParent() == null
                                            || !invoice.getStudent()
                                                    .getParent()
                                                    .getId()
                                                    .equals(actor.getId())))
                                throw new TuitionOperationException(
                                        NOT_FOUND, "Không tìm thấy học phí của con bạn.");
                            return intents
                                    .findByInvoiceIdAndStatus(
                                            invoiceId, PaymentIntentStatus.UNCERTAIN.code())
                                    .stream()
                                    .map(TuitionPaymentIntent::getId)
                                    .toList();
                        });
        for (Long id : uncertain) refreshUncertain(id);
    }

    private void refreshUncertain(Long id) {
        TuitionPaymentIntent snapshot =
                transactionTemplate.execute(
                        status ->
                                intents.findById(id)
                                        .orElseThrow(TuitionOperationException::notFound));
        if (PaymentIntentStatus.UNCERTAIN.code().equals(snapshot.getStatus())
                && !snapshot.isSimulated()
                && payOSGateway.isMissingOrClosed(snapshot.getOrderCode())) {
            transactionTemplate.executeWithoutResult(
                    status -> {
                        tuitionLedgerService.lock(snapshot.getInvoice().getId());
                        TuitionPaymentIntent saved =
                                intents.findById(id)
                                        .orElseThrow(TuitionOperationException::notFound);
                        if (PaymentIntentStatus.UNCERTAIN.code().equals(saved.getStatus()))
                            saved.setStatus(PaymentIntentStatus.FAILED.code());
                    });
        }
    }

    /**
     * Changing to cash closes unpaid links at the payOSGateway first; the cash writer rechecks
     * under lock.
     */
    @Override
    public void prepareCashPayment(Long invoiceId) {
        try {
            closeUnpaidRequests(invoiceId);
        } catch (TuitionOperationException failure) {
            if ("PAYMENT_REQUEST_NOT_CLOSED".equals(failure.getCode()))
                throw conflict(
                        "QR đã nhận tiền hoặc đang cần xác minh. Không thu thêm tiền mặt; vui lòng"
                            + " tải lại hóa đơn.");
            throw failure;
        }
    }

    @Override
    public void closeUnpaidRequests(Long invoiceId) {
        if (!List.of(Role.STAFF, Role.ADMIN, Role.BRANCH_MANAGER)
                .contains(currentUserService.getCurrentUser().getRole()))
            throw new TuitionOperationException(
                    FORBIDDEN, "Chỉ Staff hoặc Admin được đóng yêu cầu thanh toán.");
        List<Long> active =
                transactionTemplate.execute(
                        status -> {
                            tuitionLedgerService.lock(invoiceId);
                            if (!intents.findByInvoiceIdAndStatus(
                                            invoiceId, PaymentIntentStatus.CREATING.code())
                                    .isEmpty())
                                throw conflict(
                                        "QR đang được tạo. Vui lòng đợi hoàn tất trước khi đóng yêu"
                                            + " cầu thanh toán.");
                            List<TuitionPaymentIntent> pending =
                                    new ArrayList<>(
                                            intents.findByInvoiceIdAndStatus(
                                                    invoiceId, PaymentIntentStatus.PENDING.code()));
                            pending.addAll(
                                    intents.findByInvoiceIdAndStatus(
                                            invoiceId, PaymentIntentStatus.UNCERTAIN.code()));
                            return pending.stream().map(TuitionPaymentIntent::getId).toList();
                        });
        for (Long id : active) {
            cancel(id);
            String outcome =
                    transactionTemplate.execute(
                            status ->
                                    intents.findById(id)
                                            .orElseThrow(TuitionOperationException::notFound)
                                            .getStatus());
            if (!List.of(PaymentIntentStatus.CANCELLED.code(), PaymentIntentStatus.FAILED.code())
                    .contains(outcome))
                throw new TuitionOperationException(
                        CONFLICT,
                        "PAYMENT_REQUEST_NOT_CLOSED",
                        "QR đã nhận tiền hoặc đang cần xác minh. Chưa thể đóng yêu cầu; vui lòng"
                                + " tải lại hóa đơn.");
        }
    }

    @Override
    public void prepareCashPayment(Long invoiceId, BigDecimal amount, BigDecimal given) {
        transactionTemplate.executeWithoutResult(
                status -> {
                    Invoice invoice = tuitionLedgerService.lock(invoiceId);
                    tuitionLedgerService.requireCurrentInstallment(
                            invoice, tuitionLedgerService.ensure(invoice), amount);
                    CashTenderPolicy.quote(amount, given);
                });
        prepareCashPayment(invoiceId);
    }

    @Override
    public void cancel(Long id) {
        transactionTemplate.executeWithoutResult(
                status ->
                        tuitionLedgerService.checkParent(
                                intents.findById(id)
                                        .orElseThrow(TuitionOperationException::notFound)
                                        .getInvoice()));
        cancelIntent(id);
    }

    private void cancelIntent(Long id) {
        TuitionPaymentIntent snapshot =
                transactionTemplate.execute(
                        status -> {
                            TuitionPaymentIntent intent =
                                    intents.findById(id)
                                            .orElseThrow(TuitionOperationException::notFound);
                            return intent;
                        });
        refreshBankTransfers(id);
        var refreshed =
                transactionTemplate.execute(
                        status ->
                                intents.findById(id)
                                        .orElseThrow(TuitionOperationException::notFound));
        if (List.of(
                        PaymentIntentStatus.PAID.code(),
                        PaymentIntentStatus.PARTIALLY_PAID.code(),
                        PaymentIntentStatus.CANCELLED.code(),
                        PaymentIntentStatus.FAILED.code(),
                        PaymentIntentStatus.RECONCILE.code())
                .contains(refreshed.getStatus())) return;
        if (!snapshot.isSimulated()) {
            try {
                payOSGateway.cancelPayment(snapshot.getOrderCode());
            } catch (RuntimeException failure) {
                // Authenticated payOSGateway lookup handles money received before a delayed
                // webhook.
                PayOSGateway.GatewayPayment remote;
                try {
                    remote = payOSGateway.paymentInformation(snapshot.getOrderCode());
                } catch (RuntimeException lookupFailure) {
                    throw failure;
                }
                if (remote != null
                        && remote.paid().signum() > 0
                        && !remote.transfers().isEmpty()
                        && remote.transfers().stream()
                                        .map(PayOSGateway.BankTransfer::amount)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                                        .compareTo(remote.paid())
                                == 0) {
                    for (PayOSGateway.BankTransfer transfer : remote.transfers())
                        paymentResults.applyVerifiedTransfer(
                                new VerifiedBankTransfer(
                                        snapshot.getOrderCode(),
                                        transfer.amount(),
                                        transfer.reference(),
                                        transfer.content(),
                                        transfer.senderName(),
                                        transfer.senderAccount(),
                                        transfer.senderBank(),
                                        transfer.transferredAt(),
                                        false));
                    return;
                }
                throw failure;
            }
        }
        transactionTemplate.executeWithoutResult(
                status -> {
                    tuitionLedgerService.lock(snapshot.getInvoice().getId());
                    TuitionPaymentIntent intent =
                            intents.findById(id).orElseThrow(TuitionOperationException::notFound);
                    if (List.of(
                                    PaymentIntentStatus.PENDING.code(),
                                    PaymentIntentStatus.CREATING.code(),
                                    PaymentIntentStatus.UNCERTAIN.code())
                            .contains(intent.getStatus()))
                        intent.setStatus(PaymentIntentStatus.CANCELLED.code());
                });
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30000, initialDelay = 15000)
    @Override
    public void expireUnpaidRequests() {
        if (qrLifetimeSeconds <= 0) return;
        List<Long> expired =
                transactionTemplate.execute(
                        status ->
                                intents.findExpiredIds(
                                        java.time.LocalDateTime.now()
                                                .minusSeconds(qrLifetimeSeconds)));
        for (Long id : expired) {
            try {
                cancelIntent(id);
            } catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(getClass())
                        .warn("QR {} expiry could not be confirmed; kept blocked for retry", id);
            }
        }
    }

    @Override
    public PaymentReceiptResponse simulate(Long id) {
        if (!simulation) throw new TuitionOperationException(FORBIDDEN, "Mô phỏng chưa được bật.");
        TuitionPaymentIntent intent =
                transactionTemplate.execute(
                        status ->
                                intents.findById(id)
                                        .orElseThrow(TuitionOperationException::notFound));
        if (!intent.isSimulated()) throw conflict("Đây là yêu cầu thanh toán thật.");
        return paymentResults.applyVerifiedTransfer(
                intent.getOrderCode(),
                intent.getExpectedAmount(),
                "DEMO-" + intent.getOrderCode(),
                true);
    }

    @Override
    public void refreshFromBank(Long id) {
        refreshBankTransfers(id);
    }

    private void refreshBankTransfers(Long id) {
        var intent =
                transactionTemplate.execute(
                        status ->
                                intents.findById(id)
                                        .orElseThrow(TuitionOperationException::notFound));
        if (intent.isSimulated()
                || !java.util.List.of("PENDING", "UNCERTAIN", "PARTIALLY_PAID")
                        .contains(intent.getStatus())) return;
        var remote = payOSGateway.paymentInformation(intent.getOrderCode());
        if (remote == null || remote.paid().signum() == 0) return;
        var sum =
                remote.transfers().stream()
                        .map(PayOSGateway.BankTransfer::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (remote.transfers().isEmpty() || sum.compareTo(remote.paid()) != 0)
            throw conflict(
                    "Ngân hàng có tiền nhưng chưa có đủ mã giao dịch để ghi nhận an toàn. Vui lòng"
                            + " chờ hoặc liên hệ trung tâm; không chuyển lại khoản đã gửi.");
        for (var transfer : remote.transfers())
            paymentResults.applyVerifiedTransfer(
                    new VerifiedBankTransfer(
                            intent.getOrderCode(),
                            transfer.amount(),
                            transfer.reference(),
                            transfer.content(),
                            transfer.senderName(),
                            transfer.senderAccount(),
                            transfer.senderBank(),
                            transfer.transferredAt(),
                            false));
    }

    private IntentView map(TuitionPaymentIntent intent) {
        BigDecimal received = BigDecimal.ZERO, remaining = intent.getExpectedAmount();
        if (intent.getInstallmentId() != null) {
            var due = bankRepository.installment(intent.getInstallmentId()).orElse(null);
            if (due != null) {
                received = bankAllocations.paid(due.getId());
                remaining = due.getAmountDue().subtract(received).max(BigDecimal.ZERO);
            }
        }
        return new IntentView(
                intent.getId(),
                intent.getOrderCode(),
                intent.getExpectedAmount(),
                intent.getStatus(),
                intent.getCheckoutUrl(),
                intent.getQrCode(),
                intent.isSimulated(),
                received,
                remaining);
    }
}
