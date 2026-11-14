package com.talent.management.features.tuition_payment.dto.response;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class InstallmentResponses {
    private InstallmentResponses() {}

    public record OverdueContact(
            Long invoiceId,
            String invoiceCode,
            String student,
            String parent,
            String phone,
            int sequence,
            LocalDate dueDate,
            BigDecimal remaining,
            long overdueDays) {}

    public record Due(
            Long id,
            int sequence,
            BigDecimal amount,
            BigDecimal paid,
            BigDecimal remaining,
            LocalDate date,
            boolean overdue) {}

    public record Receipt(
            Long id,
            String code,
            BigDecimal amount,
            BigDecimal cashGiven,
            BigDecimal changeAmount,
            String method,
            String cashier,
            LocalDateTime at,
            BigDecimal balanceAfter,
            boolean reversed,
            BigDecimal cashRoundingAmount,
            Long cashierId) {}

    public record InvoiceView(
            Long id,
            String code,
            String student,
            String parent,
            String phone,
            String className,
            BigDecimal total,
            BigDecimal paid,
            BigDecimal balance,
            String status,
            String mode,
            long revision,
            boolean reviewRequired,
            String reviewReason,
            boolean escalation,
            List<Due> installments,
            List<Receipt> receipts,
            List<IntentView> intents,
            boolean confirmed,
            BigDecimal originalAmount,
            BigDecimal discountAmount,
            String discountReason,
            LocalDateTime reservationDeadline,
            boolean reservationExpired,
            boolean reservationPending,
            List<ReservationAction> reservationHistory) {}

    public record ReservationAction(
            String action,
            String reason,
            String actor,
            LocalDateTime at,
            LocalDateTime oldDeadline,
            LocalDateTime newDeadline) {}

    public record IntentView(
            Long id,
            long orderCode,
            BigDecimal amount,
            String status,
            String checkoutUrl,
            String qrCode,
            boolean simulated) {}

    public record ChangeView(
            Long id,
            Long invoiceId,
            String invoiceCode,
            String student,
            int sequence,
            LocalDate oldDate,
            LocalDate proposedDate,
            String reason,
            String status,
            String requestedBy,
            String reviewedBy,
            String reviewNote) {}

    public record NoticeView(
            Long id,
            Long invoiceId,
            String student,
            String code,
            LocalDate date,
            BigDecimal remaining,
            String type,
            boolean read,
            int sequence,
            IntentView paymentIntent) {}
}
