package com.talent.management.features.tuition_payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReceiptResponse {

    private Long paymentId;
    private String paymentCode;
    private String invoiceCode;
    
    // Học viên & Phụ huynh
    private Long studentId;
    private String studentName;
    private String parentName;
    private String parentPhone;
    
    // Lớp học
    private String className;
    private String courseName;
    private String branchName;
    private String scheduleDescription;
    
    // Học phí
    private BigDecimal originalAmount;
    private BigDecimal discountAmount;
    private String discountReason;
    private BigDecimal finalAmount;
    
    // Thanh toán
    private BigDecimal cashGiven;
    private BigDecimal changeAmount;
    private String paymentMethod;
    private String cashierName;
    private LocalDateTime paymentDate;
    private String bankTransactionId;
    
    // Trạng thái ghi danh
    private String enrollmentStatus;
    private String message;
}
