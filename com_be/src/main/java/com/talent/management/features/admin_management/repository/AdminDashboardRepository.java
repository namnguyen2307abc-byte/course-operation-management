package com.talent.management.features.admin_management.repository;

import com.talent.management.features.admin_management.dto.response.AdminDashboardResponse.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Aggregate queries belong here; reporting never loads all payments/users into memory. */
@Repository
public class AdminDashboardRepository {
    @PersistenceContext private EntityManager em;
    public Map<String, Long> overview() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("users", count("select count(u) from User u"));
        result.put("activeUsers", count("select count(u) from User u where u.status = 'ACTIVE'"));
        result.put("inactiveUsers", count("select count(u) from User u where u.status = 'INACTIVE'"));
        result.put("teachers", count("select count(u) from User u where u.role = 'TEACHER' and u.status = 'ACTIVE'"));
        result.put("students", count("select count(s) from Student s"));
        result.put("courses", count("select count(c) from Course c"));
        result.put("openClasses", count("select count(c) from ClassEntity c where c.status = 'OPEN'"));
        result.put("enrollments", count("select count(e) from Enrollment e where e.status = 'ENROLLED'"));
        result.put("branches", count("select count(b) from Branch b"));
        result.put("rooms", count("select count(r) from Room r"));
        result.put("equipment", count("select count(e) from Equipment e"));
        result.put("unpaidInvoices", count("select count(i) from Invoice i where i.status = 'UNPAID'"));
        return result;
    }
    public Map<String, Long> roles() { return grouped("select u.role, count(u) from User u group by u.role"); }
    public Map<String, Long> requestStatuses() { return grouped("select e.status, count(e) from EnrollmentRequest e group by e.status"); }
    public Map<String, Long> queues() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("absencePending", count("select count(a) from AbsenceRequest a where a.status = 'PENDING'"));
        result.put("makeupPending", count("select count(m) from MakeupRegistration m where m.status = 'PENDING'"));
        result.put("makeupNoSlot", count("select count(m) from MakeupRegistration m where m.status = 'NO_SLOT'"));
        return result;
    }
    public BigDecimal outstanding(LocalDate overdueBefore) {
        var query = em.createQuery("select coalesce(sum(i.finalAmount), 0) from Invoice i where i.status = 'UNPAID'"
                + (overdueBefore == null ? "" : " and i.dueDate < :today"), BigDecimal.class);
        if (overdueBefore != null) query.setParameter("today", overdueBefore);
        return query.getSingleResult();
    }
    public List<RevenuePoint> revenue(LocalDate from, LocalDate to) {
        return em.createQuery("select year(p.paymentDate), month(p.paymentDate), day(p.paymentDate), sum(p.amount) "
                + "from Payment p where p.status = 'SUCCESS' and p.paymentDate >= :from and p.paymentDate < :to "
                + "group by year(p.paymentDate), month(p.paymentDate), day(p.paymentDate) "
                + "order by year(p.paymentDate), month(p.paymentDate), day(p.paymentDate)", Object[].class)
                .setParameter("from", from.atStartOfDay()).setParameter("to", to.plusDays(1).atStartOfDay())
                .getResultList().stream().map(r -> new RevenuePoint(LocalDate.of(((Number) r[0]).intValue(),
                        ((Number) r[1]).intValue(), ((Number) r[2]).intValue()), (BigDecimal) r[3])).toList();
    }
    public long transactions(LocalDate from, LocalDate to) {
        return em.createQuery("select count(p) from Payment p where p.status = 'SUCCESS' and p.paymentDate >= :from and p.paymentDate < :to", Long.class)
                .setParameter("from", from.atStartOfDay()).setParameter("to", to.plusDays(1).atStartOfDay()).getSingleResult();
    }
    public List<UpcomingTest> upcomingTests(LocalDateTime now) {
        return em.createQuery("select p.id, p.title, p.studentName, p.testDate from PlacementSchedule p where p.status = 'SCHEDULED' and p.testDate >= :now order by p.testDate, p.id", Object[].class)
                .setParameter("now", now).setMaxResults(5).getResultList().stream()
                .map(r -> new UpcomingTest((Long) r[0], (String) r[1], (String) r[2], (LocalDateTime) r[3])).toList();
    }
    private long count(String query) { return em.createQuery(query, Long.class).getSingleResult(); }
    private Map<String, Long> grouped(String query) {
        Map<String, Long> result = new LinkedHashMap<>();
        em.createQuery(query, Object[].class).getResultList().forEach(r -> result.put(r[0].toString(), (Long) r[1]));
        return result;
    }
}
