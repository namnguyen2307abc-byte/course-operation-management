package com.talent.management.features.tuition_payment.service.impl;

import static com.talent.management.features.tuition_payment.exception.TuitionOperationException.conflict;

import static org.springframework.http.HttpStatus.*;

import com.talent.management.features.tuition_payment.dto.request.ConfirmBankPaymentRequest;
import com.talent.management.features.tuition_payment.dto.response.PaymentReceiptResponse;
import com.talent.management.features.tuition_payment.exception.TuitionOperationException;
import com.talent.management.features.tuition_payment.gateway.PayOSGateway;
import com.talent.management.features.tuition_payment.gateway.VerifiedBankTransfer;
import com.talent.management.features.tuition_payment.repository.TuitionBankEventRepository;
import com.talent.management.features.tuition_payment.repository.TuitionIntentRepository;
import com.talent.management.features.tuition_payment.service.BankTransferService;
import com.talent.management.features.tuition_payment.service.PaymentResultService;
import com.talent.management.features.tuition_payment.service.TuitionLedgerService;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.service.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentResultServiceImpl implements PaymentResultService {
    private final TuitionLedgerService tuitionLedgerService;
    private final BankTransferService bankTransfers;
    private final TuitionIntentRepository intents;
    private final TuitionBankEventRepository events;
    private final CurrentUserService currentUserService;
    private final PayOSGateway payOSGateway;
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
        java.time.LocalDateTime transferredAt = null;
        try {
            if (data.get("transactionDateTime") != null)
                transferredAt =
                        java.time.LocalDateTime.parse(
                                String.valueOf(data.get("transactionDateTime")).replace(" ", "T"));
        } catch (java.time.format.DateTimeParseException ignored) {
            /* Preserve the receive timestamp when gateway time is unavailable. */
        }
        return bankTransfers.receive(
                new VerifiedBankTransfer(
                        code,
                        amount,
                        ref,
                        text(data, "description"),
                        text(data, "counterAccountName"),
                        text(data, "counterAccountNumber"),
                        text(data, "counterAccountBankName"),
                        transferredAt,
                        false));
    }

    @Override
    public PaymentReceiptResponse confirmBank(Long id, ConfirmBankPaymentRequest input) {
        User actor = currentUserService.getCurrentUser();
        if (!List.of(Role.STAFF, Role.ADMIN, Role.BRANCH_MANAGER).contains(actor.getRole()))
            throw new TuitionOperationException(
                    FORBIDDEN, "Chỉ nhân viên được xác nhận tiền ngân hàng thực nhận.");
        var intent =
                transactionTemplate.execute(
                        status -> {
                            var found =
                                    intents.findById(id)
                                            .orElseThrow(TuitionOperationException::notFound);
                            if (found.isSimulated())
                                throw conflict(
                                        "Không dùng xác nhận ngân hàng thật cho QR mô phỏng.");
                            if (found.getStatus().equals("RECONCILE"))
                                throw conflict(
                                        "Giao dịch ngân hàng chưa khớp yêu cầu; cần kiểm tra kỹ"
                                                + " thuật trước khi ghi nhận.");
                            if (!input.bankVerified())
                                throw conflict(
                                        "Phải xác nhận đã kiểm tra tiền thực nhận trên ngân hàng"
                                                + " trung tâm.");
                            if (input.reference() == null
                                    || input.reference().isBlank()
                                    || input.reference().length() > 120)
                                throw conflict(
                                        "Vui lòng nhập mã giao dịch ngân hàng từ 1 đến 120 ký tự.");
                            if (input.reason() == null
                                    || input.reason().isBlank()
                                    || input.reason().length() > 400)
                                throw conflict(
                                        "Vui lòng nhập kết quả đối chiếu ngân hàng, tối đa 400 ký"
                                                + " tự.");
                            if (input.amount() == null
                                    || input.amount().compareTo(found.getExpectedAmount()) != 0)
                                throw conflict(
                                        "Xác nhận thủ công tại QR chỉ áp dụng số tiền đúng bằng yêu"
                                            + " cầu. Không được tự phân bổ khoản khác số tiền cho"
                                            + " hóa đơn.");
                            return found;
                        });
        var result =
                bankTransfers.receive(
                        new VerifiedBankTransfer(
                                intent.getOrderCode(), input.amount(), input.reference(), false));
        if (result == null)
            throw conflict("Giao dịch chưa đủ điều kiện ghi nhận. Đã lưu dấu vết để kiểm tra.");
        transactionTemplate.executeWithoutResult(
                status -> {
                    tuitionLedgerService.lock(
                            intents.findInvoiceId(id)
                                    .orElseThrow(TuitionOperationException::notFound));
                    var event =
                            events.findByReference(input.reference().trim())
                                    .orElseThrow(TuitionOperationException::notFound);
                    if (event.getResolvedInvoice() != null
                            && !event.getResolvedInvoice()
                                    .getId()
                                    .equals(
                                            intents.findInvoiceId(id)
                                                    .orElseThrow(
                                                            TuitionOperationException::notFound)))
                        throw conflict(
                                "Mã giao dịch đã gắn với hóa đơn khác. Không"
                                        + " được xác nhận lại vào QR hiện tại.");
                    if (!event.getStatus().equals("POSTED")) return;
                    event.setReviewedBy(actor);
                    event.setReviewedAt(java.time.LocalDateTime.now());
                    event.setNote("Đã xác minh ngân hàng thủ công: " + input.reason().trim());
                });
        return result;
    }

    private String text(Map<?, ?> data, String key) {
        Object value = data.get(key);
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public PaymentReceiptResponse applyVerifiedTransfer(
            long code, BigDecimal amount, String reference, boolean simulated) {
        return bankTransfers.receive(new VerifiedBankTransfer(code, amount, reference, simulated));
    }

    @Override
    public PaymentReceiptResponse applyVerifiedTransfer(VerifiedBankTransfer transfer) {
        return bankTransfers.receive(transfer);
    }
}
