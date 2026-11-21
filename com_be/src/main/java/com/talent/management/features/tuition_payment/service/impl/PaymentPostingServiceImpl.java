package com.talent.management.features.tuition_payment.service.impl;

import com.talent.management.features.course_enrollment.repository.EnrollmentRequestRepository;
import com.talent.management.features.notification.service.NotificationService;
import com.talent.management.features.tuition_payment.exception.TuitionOperationException;
import com.talent.management.features.tuition_payment.mapper.PaymentReference;
import com.talent.management.features.tuition_payment.policy.CashTenderPolicy;
import com.talent.management.features.tuition_payment.repository.PaymentRepository;
import com.talent.management.features.tuition_payment.repository.TuitionInstallmentRepository;
import com.talent.management.features.tuition_payment.repository.TuitionLedgerRepository;
import com.talent.management.features.tuition_payment.service.PaymentPostingService;
import com.talent.management.features.tuition_payment.service.TuitionLedgerService;
import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.entity.Enrollment;
import com.talent.management.shared.entity.Invoice;
import com.talent.management.shared.entity.Payment;
import com.talent.management.shared.entity.TuitionPlan;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.EnrollmentRequestStatus;
import com.talent.management.shared.enums.EnrollmentStatus;
import com.talent.management.shared.enums.InvoiceStatus;
import com.talent.management.shared.enums.PaymentMethod;
import com.talent.management.shared.enums.PaymentStatus;
import com.talent.management.shared.enums.Role;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentPostingServiceImpl implements PaymentPostingService {
    private final TuitionLedgerRepository ledgerRepository;
    private final TuitionLedgerService tuitionLedgerService;
    private final PaymentRepository payments;
    private final TuitionInstallmentRepository installments;
    private final EnrollmentRequestRepository requests;

    private final NotificationService notificationService;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment post(
            Invoice invoice,
            TuitionPlan plan,
            BigDecimal amount,
            PaymentMethod method,
            User cashier,
            String key,
            String bankReference,
            BigDecimal cashGiven) {
        tuitionLedgerService.validateMoney(amount);
        if (amount.signum() <= 0)
            throw new TuitionOperationException(
                    HttpStatus.BAD_REQUEST,
                    "PAYMENT_AMOUNT_ZERO",
                    "Số tiền thanh toán phải lớn hơn 0đ. Không được tạo giao dịch hoặc biên lai"
                        + " 0đ.");
        CashTenderPolicy.Quote cash =
                method == PaymentMethod.CASH_AT_DESK
                        ? CashTenderPolicy.quote(amount, cashGiven)
                        : new CashTenderPolicy.Quote(BigDecimal.ZERO, BigDecimal.ZERO);
        Payment payment =
                Payment.builder()
                        .invoice(invoice)
                        .paymentCode("PAY-" + UUID.randomUUID())
                        .amount(amount)
                        .paymentMethod(method)
                        .cashier(cashier)
                        .bankTransactionId(bankReference)
                        .cashGiven(cashGiven)
                        .changeAmount(cash.change())
                        .cashRoundingAmount(cash.support())
                        .status(PaymentStatus.SUCCESS)
                        .build();
        payment.setRequestKey(key);
        payment.setBalanceAfter(tuitionLedgerService.balance(invoice).subtract(amount));
        payments.saveAndFlush(payment);
        tuitionLedgerService.allocate(payment, plan);
        BigDecimal paid = tuitionLedgerService.paid(invoice);
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
            Enrollment enrollment = ledgerRepository.lockEnrollment(linked.getId());
            if (enrollment.getStatus() == EnrollmentStatus.PENDING_PAYMENT
                    && paid.compareTo(first) >= 0) {
                ClassEntity clazz =
                        ledgerRepository.lockAndRefreshClass(enrollment.getClassEntity().getId());
                enrollment.setStatus(EnrollmentStatus.ENROLLED);
                newlyEnrolled = true;
                clazz.setCurrentStudents(
                        Optional.ofNullable(clazz.getCurrentStudents()).orElse(0) + 1);
                requests.findByEnrollmentId(enrollment.getId())
                        .ifPresent(r -> r.setStatus(EnrollmentRequestStatus.APPROVED));
            }
        }
        if (notificationService != null) {
            String receipt = PaymentReference.receipt(payment);
            String message =
                    String.format(
                            java.util.Locale.forLanguageTag("vi-VN"),
                            "%s: đã nhận %,.0f đ cho %s; còn phải đóng %,.0f đ.",
                            receipt,
                            payment.getNetReceivedAmount(),
                            invoice.getStudent().getFullName(),
                            payment.getBalanceAfter());
            if (cash.support().signum() > 0)
                message +=
                        " Trung tâm hỗ trợ làm tròn "
                                + cash.support().toPlainString()
                                + "đ; học phí ghi nhận "
                                + amount.toPlainString()
                                + "đ.";
            if (invoice.getStudent().getParent() != null) {
                notificationService.sendNotification(
                        invoice.getStudent().getParent(),
                        cashier,
                        "Đã nhận học phí",
                        message,
                        "PAYMENT_SUCCESS",
                        payment.getId(),
                        "/pages/parent-tuition.html?invoiceId=" + invoice.getId());
            }
            notificationService.sendToRoles(
                    List.of(Role.ADMIN, Role.STAFF, Role.BRANCH_MANAGER),
                    cashier,
                    "Đã ghi nhận khoản thu học phí",
                    message,
                    "PAYMENT_SUCCESS",
                    payment.getId(),
                    "/pages/tuition-receivables.html?invoiceId=" + invoice.getId());
            if (newlyEnrolled && linked.getClassEntity().getTeacher() != null) {
                notificationService.sendNotification(
                        linked.getClassEntity().getTeacher(),
                        cashier,
                        "Học viên nhập học chính thức",
                        invoice.getStudent().getFullName()
                                + " đã đủ điều kiện học lớp "
                                + linked.getClassEntity().getClassName()
                                + ".",
                        "ENROLLMENT_COMPLETED",
                        linked.getId(),
                        "/pages/teacher-classes.html");
            }
        }
        return payment;
    }
}
