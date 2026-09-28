package com.talent.management.features.tuition_payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingInvoiceResponse {

    private Long invoiceId;
    private String invoiceCode;
    
    // Thông tin học sinh
    private Long studentId;
    private String studentName;
    private LocalDate studentDob;
    private String parentName;
    private String parentPhone;
    
    // Thông tin lớp & khóa học
    private Long classId;
    private String classCode;
    private String className;
    private String courseName;
    private String branchName;
    private String roomName;
    private String scheduleDescription;
    private LocalDate classStartDate;
    
    // Thông tin học phí
    private BigDecimal originalAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private String discountType;
    private String discountReason;
    private String status;
    
    // Thông tin giữ chỗ 24h
    private LocalDateTime createdAt;
    private LocalDate dueDate;
    private Long hoursLeft;
    private boolean expired;
}
