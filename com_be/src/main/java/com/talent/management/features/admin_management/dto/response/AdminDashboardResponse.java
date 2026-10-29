package com.talent.management.features.admin_management.dto.response;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public record AdminDashboardResponse(LocalDate from, LocalDate to, Map<String, Long> overview,
        Map<String, Long> roles, Map<String, Long> enrollmentRequests, Map<String, Long> queues,
        BigDecimal revenue, long successfulTransactions, BigDecimal outstandingAmount,
        BigDecimal overdueAmount, List<RevenuePoint> revenueByDay, List<UpcomingTest> upcomingTests) {
    public record RevenuePoint(LocalDate date, BigDecimal amount) {}
    public record UpcomingTest(Long id, String title, String studentName, LocalDateTime testDate) {}
}
