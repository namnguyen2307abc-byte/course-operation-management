package com.talent.management.features.tuition_payment.controller;

import com.talent.management.features.tuition_payment.dto.request.ApplyDiscountRequest;
import com.talent.management.features.tuition_payment.dto.request.CreateReservationRequest;
import com.talent.management.features.tuition_payment.dto.request.ProcessPaymentRequest;
import com.talent.management.features.tuition_payment.dto.response.CashierDashboardStatsResponse;
import com.talent.management.features.tuition_payment.dto.response.InvoicePaymentStatusResponse;
import com.talent.management.features.tuition_payment.dto.response.PaymentReceiptResponse;
import com.talent.management.features.tuition_payment.dto.response.PendingInvoiceResponse;
import com.talent.management.features.tuition_payment.dto.response.VietQrResponse;
import com.talent.management.features.tuition_payment.service.TuitionPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/tuition-payment")
@RequiredArgsConstructor
@Tag(name = "4. Nghĩa vụ Thanh toán học phí", description = "Quản lý hóa đơn học phí, tra cứu quầy thu ngân, miễn giảm học phí, thanh toán VietQR và ghi danh học viên")
public class TuitionPaymentController {

    private final TuitionPaymentService tuitionPaymentService;

    @GetMapping("/search")
    @Operation(summary = "Thu ngân tra cứu phiếu giữ chỗ theo số điện thoại phụ huynh hoặc tên học sinh")
    public ResponseEntity<List<PendingInvoiceResponse>> searchPendingInvoices(
            @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(tuitionPaymentService.searchPendingInvoices(keyword));
    }

    @GetMapping("/{invoiceId}")
    @Operation(summary = "Xem chi tiết một hóa đơn học phí")
    public ResponseEntity<PendingInvoiceResponse> getInvoiceDetail(@PathVariable Long invoiceId) {
        return ResponseEntity.ok(tuitionPaymentService.getInvoiceDetail(invoiceId));
    }

    @PostMapping("/{invoiceId}/apply-discount")
    @Operation(summary = "Xác nhận số tiền: Tính toán miễn giảm phí (đăng ký sớm, anh chị em, học bổng 100%)")
    public ResponseEntity<PendingInvoiceResponse> calculateDiscount(
            @PathVariable Long invoiceId,
            @Valid @RequestBody ApplyDiscountRequest request
    ) {
        return ResponseEntity.ok(tuitionPaymentService.calculateDiscount(invoiceId, request));
    }

    @GetMapping("/{invoiceId}/vietqr")
    @Operation(summary = "Sinh mã QR VietQR động thanh toán học phí qua ngân hàng")
    public ResponseEntity<VietQrResponse> generateVietQr(
            @PathVariable Long invoiceId,
            @RequestParam(required = false) BigDecimal amount
    ) {
        return ResponseEntity.ok(tuitionPaymentService.generateVietQr(invoiceId, amount));
    }

    @PostMapping("/process")
    @Operation(summary = "Thu ngân bấm xác nhận đã thu tiền & Hoàn tất ghi danh chính thức vào lớp học")
    public ResponseEntity<PaymentReceiptResponse> processPayment(
            @Valid @RequestBody ProcessPaymentRequest request,
            Authentication authentication
    ) {
        String cashierUsername = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(tuitionPaymentService.processPayment(request, cashierUsername));
    }

    @GetMapping("/{invoiceId}/status")
    @Operation(summary = "Kiểm tra trạng thái thanh toán của hóa đơn (phục vụ tự động nhận diện thanh toán Auto-Detect)")
    public ResponseEntity<InvoicePaymentStatusResponse> checkPaymentStatus(@PathVariable Long invoiceId) {
        return ResponseEntity.ok(tuitionPaymentService.checkPaymentStatus(invoiceId));
    }

    @PostMapping("/{invoiceId}/bank-webhook-simulate")
    @Operation(summary = "Mô phỏng Webhook Ngân Hàng báo có (Ting-Ting tự động kích hoạt thanh toán)")
    public ResponseEntity<PaymentReceiptResponse> simulateBankWebhook(
            @PathVariable Long invoiceId,
            @RequestParam(required = false) BigDecimal amount
    ) {
        return ResponseEntity.ok(tuitionPaymentService.simulateBankWebhook(invoiceId, amount));
    }

    @PostMapping("/reservations")
    @Operation(summary = "Tạo phiếu giữ chỗ tạm thời trong vòng 24 giờ cho học sinh")
    public ResponseEntity<PendingInvoiceResponse> createReservation(
            @Valid @RequestBody CreateReservationRequest request,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tuitionPaymentService.createDemoReservation(request, username));
    }

    @GetMapping("/history")
    @Operation(summary = "Lịch sử thu học phí tại quầy thu ngân (hỗ trợ tìm kiếm theo từ khóa)")
    public ResponseEntity<List<PaymentReceiptResponse>> getPaymentHistory(
            @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(tuitionPaymentService.getPaymentHistory(keyword));
    }

    @GetMapping("/stats")
    @Operation(summary = "Thống kê tổng quan quầy thu ngân (số phiếu chờ, đã thu, doanh thu)")
    public ResponseEntity<CashierDashboardStatsResponse> getDashboardStats() {
        return ResponseEntity.ok(tuitionPaymentService.getDashboardStats());
    }

    @GetMapping("/fix-font")
    @Operation(summary = "Chuẩn hóa font chữ tiếng Việt Unicode trong CSDL")
    public ResponseEntity<String> fixFont() {
        tuitionPaymentService.fixVietnameseFontData();
        return ResponseEntity.ok("Chuẩn hóa font tiếng Việt thành công!");
    }
}
