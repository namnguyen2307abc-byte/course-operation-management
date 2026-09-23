package com.talent.management.features.tuition_payment.mapper;

import com.talent.management.features.tuition_payment.dto.response.PaymentReceiptResponse;
import com.talent.management.features.tuition_payment.dto.response.PendingInvoiceResponse;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.PaymentMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class TuitionPaymentMapper {

    public PendingInvoiceResponse toPendingInvoiceResponse(Invoice invoice) {
        if (invoice == null) {
            return null;
        }

        Student student = invoice.getStudent();
        User parent = student != null ? student.getParent() : null;
        Enrollment enrollment = invoice.getEnrollment();
        ClassEntity classEntity = enrollment != null ? enrollment.getClassEntity() : null;
        Course course = classEntity != null ? classEntity.getCourse() : null;
        Branch branch = classEntity != null ? classEntity.getBranch() : null;
        Room room = classEntity != null ? classEntity.getRoom() : null;

        // Tính thời gian giữ chỗ 24h
        LocalDateTime createdAt = invoice.getCreatedAt() != null ? invoice.getCreatedAt() : LocalDateTime.now();
        LocalDateTime expiresAt = createdAt.plusHours(24);
        LocalDateTime now = LocalDateTime.now();
        long hoursLeft = Duration.between(now, expiresAt).toHours();
        boolean expired = now.isAfter(expiresAt);

        return PendingInvoiceResponse.builder()
                .invoiceId(invoice.getId())
                .invoiceCode(invoice.getInvoiceCode())
                .studentId(student != null ? student.getId() : null)
                .studentName(student != null ? student.getFullName() : "N/A")
                .studentDob(student != null ? student.getDateOfBirth() : null)
                .parentName(parent != null ? parent.getFullName() : "Khách vãng lai")
                .parentPhone(parent != null ? parent.getPhone() : "Chưa có SĐT")
                .classId(classEntity != null ? classEntity.getId() : null)
                .classCode(classEntity != null ? classEntity.getClassCode() : "")
                .className(classEntity != null ? classEntity.getClassName() : "Chưa xếp lớp")
                .courseName(course != null ? course.getName() : "")
                .branchName(branch != null ? branch.getName() : "")
                .roomName(room != null ? room.getRoomName() : "")
                .scheduleDescription(classEntity != null ? classEntity.getScheduleDescription() : "")
                .classStartDate(classEntity != null ? classEntity.getStartDate() : null)
                .originalAmount(invoice.getOriginalAmount())
                .discountAmount(invoice.getDiscountAmount() != null ? invoice.getDiscountAmount() : BigDecimal.ZERO)
                .finalAmount(invoice.getFinalAmount())
                .discountType(invoice.getDiscountType() != null ? invoice.getDiscountType().name() : "NONE")
                .discountReason(invoice.getDiscountReason())
                .status(invoice.getStatus() != null ? invoice.getStatus().name() : "UNPAID")
                .createdAt(invoice.getCreatedAt())
                .dueDate(invoice.getDueDate())
                .hoursLeft(Math.max(0, hoursLeft))
                .expired(expired)
                .build();
    }

    public PaymentReceiptResponse toPaymentReceiptResponse(Payment payment, BigDecimal cashGiven, BigDecimal changeAmount) {
        if (payment == null) {
            return null;
        }

        Invoice invoice = payment.getInvoice();
        Student student = invoice != null ? invoice.getStudent() : null;
        User parent = student != null ? student.getParent() : null;
        Enrollment enrollment = invoice != null ? invoice.getEnrollment() : null;
        ClassEntity classEntity = enrollment != null ? enrollment.getClassEntity() : null;
        Course course = classEntity != null ? classEntity.getCourse() : null;
        Branch branch = classEntity != null ? classEntity.getBranch() : null;
        User cashier = payment.getCashier();

        BigDecimal actualCashGiven = (payment.getPaymentMethod() == PaymentMethod.CASH_AT_DESK) ? cashGiven : null;
        BigDecimal actualChangeAmount = (payment.getPaymentMethod() == PaymentMethod.CASH_AT_DESK) ? changeAmount : null;

        return PaymentReceiptResponse.builder()
                .paymentId(payment.getId())
                .paymentCode(payment.getPaymentCode())
                .invoiceCode(invoice != null ? invoice.getInvoiceCode() : "")
                .studentId(student != null ? student.getId() : null)
                .studentName(student != null ? student.getFullName() : "")
                .parentName(parent != null ? parent.getFullName() : "")
                .parentPhone(parent != null ? parent.getPhone() : "")
                .className(classEntity != null ? classEntity.getClassName() : "")
                .courseName(course != null ? course.getName() : "")
                .branchName(branch != null ? branch.getName() : "")
                .scheduleDescription(classEntity != null ? classEntity.getScheduleDescription() : "")
                .originalAmount(invoice != null ? invoice.getOriginalAmount() : payment.getAmount())
                .discountAmount(invoice != null && invoice.getDiscountAmount() != null ? invoice.getDiscountAmount() : BigDecimal.ZERO)
                .discountReason(invoice != null ? invoice.getDiscountReason() : null)
                .finalAmount(payment.getAmount())
                .cashGiven(actualCashGiven)
                .changeAmount(actualChangeAmount)
                .paymentMethod(payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : "")
                .cashierName(cashier != null ? cashier.getFullName() : "Thu Ngân Quầy")
                .paymentDate(payment.getPaymentDate())
                .bankTransactionId(payment.getBankTransactionId())
                .enrollmentStatus(enrollment != null && enrollment.getStatus() != null ? enrollment.getStatus().name() : "ENROLLED")
                .message("Thanh toán thành công! Học viên đã chính thức được ghi danh vào lớp học.")
                .build();
    }
}
