package com.talent.management.features.tuition_payment.policy;

import com.talent.management.features.tuition_payment.exception.BankTransferValidationException;
import com.talent.management.features.tuition_payment.exception.TuitionValidationException;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.InvoiceStatus;

import java.math.BigDecimal;
import java.util.Map;

public final class BankTransferPolicy {
    private BankTransferPolicy() {}

    public static void money(BigDecimal amount, String field) {
        if (amount == null) throw invalid(field, "Thiếu số tiền ngân hàng thực nhận.");
        if (amount.signum() <= 0)
            throw invalid(field, "Số tiền ngân hàng thực nhận phải lớn hơn 0đ.");
        if (amount.stripTrailingZeros().scale() > 0)
            throw invalid(
                    field,
                    "Số tiền chuyển khoản phải là số nguyên đồng, không được có phần thập phân.");
        if (amount.compareTo(new BigDecimal("9999999999")) > 0)
            throw invalid(field, "Số tiền không được vượt 9.999.999.999đ.");
    }

    public static void open(Invoice invoice, TuitionPlan plan) {
        if (invoice.getStatus() == InvoiceStatus.CANCELLED)
            throw invalid(
                    "invoiceId", "Hóa đơn đã hủy. Không được phân bổ giao dịch vào hóa đơn này.");
        if (invoice.getStatus() == InvoiceStatus.PAID)
            throw invalid("invoiceId", "Hóa đơn đã đóng đủ. Không ghi nhận thêm vào hóa đơn này.");
        if (plan.getCreatedBy() == null)
            throw invalid(
                    "invoiceId",
                    "Hóa đơn chưa chốt ưu đãi và lịch đóng. Vui lòng chốt lịch trước khi ghi nhận"
                            + " chuyển khoản.");
        if (plan.isReviewRequired())
            throw invalid(
                    "invoiceId",
                    "Công nợ hóa đơn chưa khớp. Vui lòng kiểm tra dữ liệu thu cũ trước khi phân"
                            + " bổ.");
    }

    public static TuitionValidationException invalid(String field, String message) {
        return new BankTransferValidationException(Map.of(field, message));
    }
}
