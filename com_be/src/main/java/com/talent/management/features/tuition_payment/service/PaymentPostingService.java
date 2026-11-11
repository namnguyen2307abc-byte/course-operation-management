package com.talent.management.features.tuition_payment.service;

import static com.talent.management.features.tuition_payment.service.TuitionPlanService.conflict;

import com.talent.management.features.course_enrollment.repository.EnrollmentRequestRepository;
import com.talent.management.features.tuition_payment.repository.*;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import com.talent.management.shared.service.CurrentUserService;

import jakarta.persistence.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentPostingService {
    private final TuitionPlanService ledger;
    private final PaymentRepository payments;
    private final TuitionPlanRepository plans;
    private final TuitionInstallmentRepository installments;
    private final EnrollmentRequestRepository requests;
    private final CurrentUserService current;
    private final EntityManager em;
    private final com.talent.management.features.tuition_payment.mapper.TuitionPaymentMapper mapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.talent.management.features.notification.service.NotificationService notificationService;

    @Transactional
    public com.talent.management.features.tuition_payment.dto.response.PaymentReceiptResponse
            collectReceipt(Long invoiceId, BigDecimal amount, BigDecimal cashGiven, String key) {
        return mapper.toPaymentReceiptResponse(cash(invoiceId, amount, cashGiven, key), null, null);
    }

    @Transactional
    public Payment cash(Long invoiceId, BigDecimal amount, BigDecimal cashGiven, String key) {
        Invoice invoice = ledger.lock(invoiceId);
        if (key == null || key.isBlank() || key.length() > 80)
            throw conflict("Thiếu mã chống gửi lặp.");
        User actor = current.getCurrentUser();
        String scoped = "CASH-" + key;
        Optional<Payment> duplicate = payments.findByRequestKey(scoped);
        if (duplicate.isPresent()) {
            Payment old = duplicate.get();
            if (!old.getInvoice().getId().equals(invoiceId)
                    || old.getAmount().compareTo(amount) != 0
                    || cashGiven == null
                    || old.getCashGiven().compareTo(cashGiven) != 0
                    || old.getCashier() == null
                    || !old.getCashier().getId().equals(actor.getId())) {
                throw conflict("Mã yêu cầu đã dùng với nội dung khác.");
            }
            return old;
        }
        TuitionPlan plan = ledger.ensure(invoice);
        if (em.createQuery(
                                "select count(i) from TuitionPaymentIntent i where i.invoice.id=:id"
                                        + " and i.status in ('CREATING','PENDING','UNCERTAIN')",
                                Long.class)
                        .setParameter("id", invoiceId)
                        .getSingleResult()
                > 0) {
            throw conflict(
                    "Đang có QR chờ thanh toán. Đóng QR chưa trả hoặc xác nhận tiền chuyển khoản đã nhận trước khi thu tiền mặt.");
        }
        ledger.requireCollectible(invoice, plan, amount);
        ledger.validateMoney(cashGiven);
        if (cashGiven.compareTo(amount) < 0) throw conflict("Tiền khách đưa chưa đủ.");
        return post(
                invoice, plan, amount, PaymentMethod.CASH_AT_DESK, actor, scoped, null, cashGiven);
    }

    public Payment post(
            Invoice invoice,
            TuitionPlan plan,
            BigDecimal amount,
            PaymentMethod method,
            User cashier,
            String key,
            String bankReference,
            BigDecimal cashGiven) {
        Payment payment =
                Payment.builder()
                        .invoice(invoice)
                        .paymentCode("PAY-" + UUID.randomUUID())
                        .amount(amount)
                        .paymentMethod(method)
                        .cashier(cashier)
                        .bankTransactionId(bankReference)
                        .cashGiven(cashGiven)
                        .changeAmount(cashGiven.subtract(amount))
                        .status(PaymentStatus.SUCCESS)
                        .build();
        payment.setRequestKey(key);
        payment.setBalanceAfter(ledger.balance(invoice).subtract(amount));
        payments.saveAndFlush(payment);
        ledger.allocate(payment, plan);
        BigDecimal paid = ledger.paid(invoice);
        invoice.setStatus(
                paid.compareTo(invoice.getFinalAmount()) == 0
                        ? InvoiceStatus.PAID
                        : InvoiceStatus.PARTIALLY_PAID);
        plan.setRevision(plan.getRevision() + 1);
        BigDecimal first =
                installments.findByPlanIdOrderBySequenceNumber(plan.getId()).get(0).getAmountDue();
        Enrollment linked = invoice.getEnrollment();
        boolean newlyEnrolled = false;
        if (linked != null) {
            Enrollment enrollment =
                    em.find(Enrollment.class, linked.getId(), LockModeType.PESSIMISTIC_WRITE);
            if (enrollment.getStatus() == EnrollmentStatus.PENDING_PAYMENT
                    && paid.compareTo(first) >= 0) {
                ClassEntity clazz =
                        em.find(
                                ClassEntity.class,
                                enrollment.getClassEntity().getId(),
                                LockModeType.PESSIMISTIC_WRITE);
                em.refresh(clazz, LockModeType.PESSIMISTIC_WRITE);
                enrollment.setStatus(EnrollmentStatus.ENROLLED);
                newlyEnrolled = true;
                clazz.setCurrentStudents(
                        Optional.ofNullable(clazz.getCurrentStudents()).orElse(0) + 1);
                requests.findByEnrollmentId(enrollment.getId())
                        .ifPresent(r -> r.setStatus(EnrollmentRequestStatus.APPROVED));
            }
        }
        if (notificationService != null) {
            String receipt = com.talent.management.features.tuition_payment.mapper.PaymentReference.receipt(payment);
            String message = String.format(java.util.Locale.forLanguageTag("vi-VN"),
                    "%s: đã nhận %,.0f đ cho %s; còn phải đóng %,.0f đ.",
                    receipt, amount, invoice.getStudent().getFullName(), payment.getBalanceAfter());
            if (invoice.getStudent().getParent() != null) {
                notificationService.sendNotification(invoice.getStudent().getParent(), cashier,
                        "Đã nhận học phí", message, "PAYMENT_SUCCESS", payment.getId(),
                        "/pages/parent-tuition.html?invoiceId=" + invoice.getId());
            }
            notificationService.sendToRoles(List.of(Role.ADMIN, Role.STAFF), cashier,
                    "Đã ghi nhận khoản thu học phí", message, "PAYMENT_SUCCESS", payment.getId(),
                    "/pages/tuition-receivables.html?invoiceId=" + invoice.getId());
            if (newlyEnrolled && linked.getClassEntity().getTeacher() != null) {
                notificationService.sendNotification(linked.getClassEntity().getTeacher(), cashier,
                        "Học viên nhập học chính thức",
                        invoice.getStudent().getFullName() + " đã đủ điều kiện học lớp "
                                + linked.getClassEntity().getClassName() + ".",
                        "ENROLLMENT_COMPLETED", linked.getId(), "/pages/teacher-classes.html");
            }
        }
        return payment;
    }
}
