package com.talent.management.features.admin_management.service.impl;

import com.talent.management.features.admin_management.service.AdminAuditService;
import com.talent.management.features.admin_management.repository.AdminAuditLogRepository;
import com.talent.management.features.admin_management.dto.response.AdminAuditResponse;
import com.talent.management.shared.dto.PageResponse;
import com.talent.management.shared.entity.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AdminAuditServiceImpl implements AdminAuditService {
    private final AdminAuditLogRepository repository;

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void record(User actor, User target, String action, String details) {
        repository.save(AdminAuditLog.builder().actorId(actor.getId()).actorUsername(actor.getUsername())
                .targetId(target.getId()).targetUsername(target.getUsername()).action(action).details(details)
                .createdAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))).build());
    }

    @Override @Transactional(readOnly = true)
    public PageResponse<AdminAuditResponse> search(String keyword, String action, Long targetId, LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng ngày không hợp lệ");
        var result = repository.findAll((root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String value = "%" + keyword.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                conditions.add(cb.or(cb.like(cb.lower(root.get("actorUsername")), value, '\\'),
                        cb.like(cb.lower(root.get("targetUsername")), value, '\\')));
            }
            if (action != null && !action.isBlank()) conditions.add(cb.equal(root.get("action"), action));
            if (targetId != null) conditions.add(cb.equal(root.get("targetId"), targetId));
            if (from != null) conditions.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            if (to != null) conditions.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            return cb.and(conditions.toArray(Predicate[]::new));
        }, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return PageResponse.from(result.map(a -> new AdminAuditResponse(a.getId(), a.getActorId(), a.getActorUsername(),
                a.getTargetId(), a.getTargetUsername(), a.getAction(), a.getDetails(), a.getCreatedAt())));
    }
}
