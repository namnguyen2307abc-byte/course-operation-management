package com.talent.management.features.admin_management.service.impl;
import com.talent.management.features.admin_management.repository.AdminDashboardRepository;
import com.talent.management.features.admin_management.dto.response.AdminDashboardResponse;
import com.talent.management.features.admin_management.dto.response.AdminDashboardResponse.RevenuePoint;
import com.talent.management.features.admin_management.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class AdminDashboardServiceImpl implements AdminDashboardService {
    private final AdminDashboardRepository repository;
    @Override
    public AdminDashboardResponse overview(LocalDate from, LocalDate to) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        if (from == null) from = now.toLocalDate().withDayOfMonth(1);
        if (to == null) to = now.toLocalDate();
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 366) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chọn khoảng ngày hợp lệ, tối đa 367 ngày");
        Map<LocalDate, BigDecimal> amounts = repository.revenue(from, to).stream().collect(Collectors.toMap(RevenuePoint::date, RevenuePoint::amount));
        List<RevenuePoint> chart = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) chart.add(new RevenuePoint(day, amounts.getOrDefault(day, BigDecimal.ZERO)));
        BigDecimal revenue = chart.stream().map(RevenuePoint::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AdminDashboardResponse(from, to, repository.overview(), repository.roles(), repository.requestStatuses(), repository.queues(),
                revenue, repository.transactions(from, to), repository.outstanding(null), repository.outstanding(now.toLocalDate()), chart, repository.upcomingTests(now));
    }
}
