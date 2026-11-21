package com.talent.management.features.tuition_payment.repository;

import com.talent.management.features.placement_test.entity.PlacementSchedule;
import com.talent.management.features.tuition_payment.entity.TuitionDiscountSnapshot;
import com.talent.management.shared.entity.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Custom persistence operations; callers own transaction boundaries and business decisions. */
@Repository
@RequiredArgsConstructor
public class TuitionLedgerRepository {
    private final EntityManager entityManager;

    public Optional<Invoice> lockInvoice(Long id) {
        return Optional.ofNullable(
                entityManager.find(Invoice.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    public Enrollment lockEnrollment(Long id) {
        return entityManager.find(Enrollment.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    public ClassEntity lockAndRefreshClass(Long id) {
        ClassEntity classroom =
                entityManager.find(ClassEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(classroom, LockModeType.PESSIMISTIC_WRITE);
        return classroom;
    }

    public List<Invoice> findOutstandingInvoices() {
        return entityManager
                .createQuery(
                        "select i from Invoice i where i.status in ('UNPAID','PARTIALLY_PAID')",
                        Invoice.class)
                .getResultList();
    }

    public List<Invoice> findInvoicesForParent(Long parentId) {
        return entityManager
                .createQuery(
                        "select i from Invoice i where i.student.parent.id=:id order by i.id desc",
                        Invoice.class)
                .setParameter("id", parentId)
                .getResultList();
    }

    public List<Invoice> findAllInvoicesNewestFirst() {
        return entityManager
                .createQuery("select i from Invoice i order by i.id desc", Invoice.class)
                .getResultList();
    }

    public BigDecimal sumSuccessfulTuition(Long invoiceId) {
        return entityManager
                .createQuery(
                        "select coalesce(sum(p.amount),0) from Payment p where p.invoice.id=:id and"
                            + " p.status='SUCCESS' and not exists (select r.id from TuitionReversal"
                            + " r where r.payment=p and r.status='APPROVED')",
                        BigDecimal.class)
                .setParameter("id", invoiceId)
                .getSingleResult();
    }

    public boolean hasIntents(Long invoiceId, Collection<String> statuses) {
        return entityManager
                        .createQuery(
                                "select count(i) from TuitionPaymentIntent i where i.invoice.id=:id"
                                        + " and i.status in :statuses",
                                Long.class)
                        .setParameter("id", invoiceId)
                        .setParameter("statuses", statuses)
                        .getSingleResult()
                > 0;
    }

    public boolean hasPlanChanges(Long planId) {
        return entityManager
                        .createQuery(
                                "select count(c) from TuitionPlanChange c where c.plan.id=:id",
                                Long.class)
                        .setParameter("id", planId)
                        .getSingleResult()
                > 0;
    }

    public boolean hasInstallmentPlan(Long invoiceId) {
        return entityManager
                        .createQuery(
                                "select count(p) from TuitionPlan p where p.invoice.id=:id and"
                                        + " p.mode='INSTALLMENTS'",
                                Long.class)
                        .setParameter("id", invoiceId)
                        .getSingleResult()
                > 0;
    }

    public void deleteNoticesForPlan(Long planId) {
        entityManager
                .createQuery("delete from TuitionNotice n where n.installment.plan.id=:id")
                .setParameter("id", planId)
                .executeUpdate();
    }

    public List<TuitionReservationAction> findReservationActions(Long invoiceId) {
        return entityManager
                .createQuery(
                        "select a from TuitionReservationAction a where a.invoice.id=:id order by"
                                + " a.id desc",
                        TuitionReservationAction.class)
                .setParameter("id", invoiceId)
                .getResultList();
    }

    public void saveReservationAction(TuitionReservationAction action) {
        entityManager.persist(action);
        entityManager.flush();
    }

    public boolean hasUnpostedBankEvents(Long invoiceId) {
        return entityManager
                        .createQuery(
                                "select count(e) from TuitionBankEvent e where"
                                        + " e.intent.invoice.id=:id and e.status='RECONCILE'",
                                Long.class)
                        .setParameter("id", invoiceId)
                        .getSingleResult()
                > 0;
    }

    public Optional<EnrollmentRequest> enrollmentRequest(Long enrollmentId) {
        return entityManager
                .createQuery(
                        "select r from EnrollmentRequest r where r.enrollment.id=:id",
                        EnrollmentRequest.class)
                .setParameter("id", enrollmentId)
                .getResultStream()
                .findFirst();
    }

    public Optional<PlacementSchedule> completedPlacement(
            Long studentId, Long courseId, java.time.LocalDateTime reviewedAt) {
        return entityManager
                .createQuery(
                        "select p from PlacementSchedule p where"
                            + " p.enrollmentRequest.student.id=:student and"
                            + " p.recommendedCourse.id=:course and p.status=:completed and"
                            + " (:reviewed is null or (p.evaluatedAt<=:reviewed or (p.evaluatedAt"
                            + " is null and p.testDate<=:reviewed))) order by p.evaluatedAt"
                            + " desc,p.id desc",
                        PlacementSchedule.class)
                .setParameter(
                        "completed",
                        com.talent.management.features.placement_test.entity.PlacementScheduleStatus
                                .COMPLETED)
                .setParameter("student", studentId)
                .setParameter("course", courseId)
                .setParameter("reviewed", reviewedAt)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    public Optional<TuitionDiscountSnapshot> discountSnapshot(Long invoiceId) {
        return Optional.ofNullable(entityManager.find(TuitionDiscountSnapshot.class, invoiceId));
    }

    public void saveDiscountSnapshot(TuitionDiscountSnapshot snapshot) {
        entityManager.persist(snapshot);
    }

    public boolean isPlanConfirmed(Long id) {
        return entityManager
                        .createQuery(
                                "select count(p) from TuitionPlan p where p.invoice.id=:id and"
                                    + " p.createdBy is not null",
                                Long.class)
                        .setParameter("id", id)
                        .getSingleResult()
                > 0;
    }
}
