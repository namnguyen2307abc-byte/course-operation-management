package com.talent.management.features.course_enrollment.service;

import com.talent.management.features.course_enrollment.dto.request.EnrollmentDecisionRequest;
import com.talent.management.features.course_enrollment.enums.EnrollmentDecision;
import com.talent.management.features.course_enrollment.mapper.CourseEnrollmentMapper;
import com.talent.management.features.course_enrollment.repository.ClassRepository;
import com.talent.management.features.placement_test.service.PlacementTestService;
import com.talent.management.features.tuition_payment.repository.InvoiceRepository;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import com.talent.management.shared.service.CurrentUserService;
import jakarta.persistence.EntityManager;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
}, showSql = false)
@Import({CourseEnrollmentService.class, CourseEnrollmentMapper.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CourseEnrollmentConcurrencyTest {
    @Autowired CourseEnrollmentService service;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired InvoiceRepository invoices;
    @Autowired ClassRepository classes;
    @MockBean CurrentUserService currentUser;
    @MockBean PlacementTestService placement;

    private final ThreadLocal<User> actor = new ThreadLocal<>();
    private TransactionTemplate transaction;
    private User firstStaff;
    private User secondStaff;
    private Long classId;
    private Long firstRequestId;
    private Long secondRequestId;

    @BeforeEach
    void prepareCommittedFixture() {
        transaction = new TransactionTemplate(transactionManager);
        when(currentUser.getCurrentUser()).thenAnswer(call -> actor.get());
        transaction.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString();
            User parent = persistUser("parent-" + suffix, Role.PARENT);
            firstStaff = persistUser("staff-a-" + suffix, Role.STAFF);
            secondStaff = persistUser("staff-b-" + suffix, Role.STAFF);
            Program program = Program.builder().code("PI-" + suffix).name("Piano").build();
            em.persist(program);
            Course course = Course.builder().program(program).code("COURSE-" + suffix)
                    .name("Piano nền tảng").tuitionFee(new BigDecimal("2400000")).build();
            em.persist(course);
            Branch branch = Branch.builder().code("BR-" + suffix).name("Mỹ Đình")
                    .address("Hà Nội").active(true).build();
            em.persist(branch);
            ClassEntity clazz = ClassEntity.builder().course(course).branch(branch)
                    .classCode("CLASS-" + suffix).className("Piano cuối tuần")
                    .status(ClassStatus.OPEN).maxStudents(8).currentStudents(6).build();
            em.persist(clazz);
            classId = clazz.getId();
            for (int i = 0; i < 7; i++) {
                Student student = persistStudent(parent, "Học viên " + i);
                em.persist(Enrollment.builder().student(student).classEntity(clazz)
                        .registeredByUser(firstStaff)
                        .status(i < 6 ? EnrollmentStatus.ENROLLED : EnrollmentStatus.PENDING_PAYMENT)
                        .build());
            }
            firstRequestId = persistRequest(parent, course, "Học viên mới A");
            secondRequestId = persistRequest(parent, course, "Học viên mới B");
            em.flush();
        });
    }

    @Test
    void competingAssignmentsReserveOnlyTheLastSeat() throws Exception {
        List<Throwable> outcomes = compete(false);
        assertThat(outcomes.get(0)).isNull();
        assertFullClass(outcomes.get(1));
        assertAfterAssignment(firstRequestId, secondRequestId);
    }

    @Test
    void failedInvoiceRollsBackAndWaitingReviewerCanTakeTheSeat() throws Exception {
        List<Throwable> outcomes = compete(true);
        assertThat(outcomes.get(0)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated invoice failure");
        assertThat(outcomes.get(1)).isNull();
        assertAfterAssignment(secondRequestId, firstRequestId);
    }

    @Test
    void sameRequestCannotCreateTwoEnrollments() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Throwable> first = workers.submit(() -> {
                ready.countDown();
                await(start);
                return assign(firstRequestId, firstStaff);
            });
            Future<Throwable> second = workers.submit(() -> {
                ready.countDown();
                await(start);
                return assign(firstRequestId, secondStaff);
            });
            await(ready);
            start.countDown();
            Throwable a = first.get(10, TimeUnit.SECONDS);
            Throwable b = second.get(10, TimeUnit.SECONDS);
            assertThat(a == null ^ b == null).isTrue();
            Throwable rejected = a == null ? b : a;
            assertThat(rejected).isInstanceOf(ResponseStatusException.class);
            assertThat(((ResponseStatusException) rejected).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
            assertAfterAssignment(firstRequestId, secondRequestId);
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void alreadyFullClassRejectsAssignmentWithoutCreatingRecords() {
        transaction.executeWithoutResult(status -> em.find(ClassEntity.class, classId).setMaxStudents(7));
        assertFullClass(assign(firstRequestId, firstStaff));
        transaction.executeWithoutResult(status -> {
            assertThat(count("select count(e) from Enrollment e where e.classEntity.id = :id"))
                    .isEqualTo(7);
            assertThat(count("select count(i) from Invoice i where i.enrollment.classEntity.id = :id"))
                    .isZero();
            assertUntouched(firstRequestId);
        });
    }

    private List<Throwable> compete(boolean failFirstInvoice) throws Exception {
        CountDownLatch firstHoldsClassLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAttemptedClassLock = new CountDownLatch(1);
        MethodInterceptor classGate = call -> {
            if (!call.getMethod().getName().equals("findByIdForUpdate")) return call.proceed();
            if (actor.get() == secondStaff) secondAttemptedClassLock.countDown();
            Object result = call.proceed();
            if (actor.get() == firstStaff) {
                firstHoldsClassLock.countDown();
                await(releaseFirst);
            }
            return result;
        };
        MethodInterceptor invoiceFailure = call -> {
            if (failFirstInvoice && call.getMethod().getName().equals("save")
                    && actor.get() == firstStaff) {
                throw new IllegalStateException("Simulated invoice failure");
            }
            return call.proceed();
        };
        // Keep real Spring Data queries and transactions; pause only after the class lock is acquired.
        Advised classProxy = (Advised) classes;
        Advised invoiceProxy = (Advised) invoices;
        classProxy.addAdvice(0, classGate);
        invoiceProxy.addAdvice(0, invoiceFailure);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = workers.submit(() -> assign(firstRequestId, firstStaff));
            await(firstHoldsClassLock);
            Future<Throwable> second = workers.submit(() -> assign(secondRequestId, secondStaff));
            await(secondAttemptedClassLock);
            // The second reviewer must wait while the first transaction owns the class lock.
            assertThatThrownBy(() -> second.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            releaseFirst.countDown();
            Throwable firstOutcome = first.get(10, TimeUnit.SECONDS);
            Throwable secondOutcome = second.get(10, TimeUnit.SECONDS);
            return java.util.Arrays.asList(firstOutcome, secondOutcome);
        } finally {
            releaseFirst.countDown();
            workers.shutdownNow();
            boolean stopped = workers.awaitTermination(10, TimeUnit.SECONDS);
            classProxy.removeAdvice(classGate);
            invoiceProxy.removeAdvice(invoiceFailure);
            assertThat(stopped).isTrue();
        }
    }

    private Throwable assign(Long requestId, User staff) {
        actor.set(staff);
        try {
            service.decide(requestId,
                    new EnrollmentDecisionRequest(EnrollmentDecision.APPROVE, classId, "Đã tư vấn"));
            return null;
        } catch (Exception exception) {
            return exception;
        } finally {
            actor.remove();
        }
    }

    private void assertAfterAssignment(Long acceptedId, Long untouchedId) {
        transaction.executeWithoutResult(status -> {
            ClassEntity clazz = em.find(ClassEntity.class, classId);
            assertThat(clazz.getCurrentStudents()).isEqualTo(6);
            long pending = em.createQuery("select count(e) from Enrollment e where e.classEntity.id = :id "
                            + "and e.status = :status", Long.class)
                    .setParameter("id", classId).setParameter("status", EnrollmentStatus.PENDING_PAYMENT)
                    .getSingleResult();
            assertThat(pending).isEqualTo(2);
            assertThat(clazz.getCurrentStudents() + pending).isEqualTo(clazz.getMaxStudents().longValue());
            assertThat(count("select count(e) from Enrollment e where e.classEntity.id = :id"))
                    .isEqualTo(8);
            assertThat(count("select count(i) from Invoice i where i.enrollment.classEntity.id = :id"))
                    .isEqualTo(1);
            EnrollmentRequest accepted = em.find(EnrollmentRequest.class, acceptedId);
            assertThat(accepted.getStatus()).isEqualTo(EnrollmentRequestStatus.PENDING_PAYMENT);
            assertThat(accepted.getClassEntity().getId()).isEqualTo(classId);
            assertThat(accepted.getEnrollment().getStatus()).isEqualTo(EnrollmentStatus.PENDING_PAYMENT);
            assertUntouched(untouchedId);
        });
    }

    private void assertUntouched(Long requestId) {
        EnrollmentRequest request = em.find(EnrollmentRequest.class, requestId);
        assertThat(request.getStatus()).isEqualTo(EnrollmentRequestStatus.READY_FOR_ASSIGNMENT);
        assertThat(request.getClassEntity()).isNull();
        assertThat(request.getEnrollment()).isNull();
        assertThat(request.getReviewedBy()).isNull();
        assertThat(request.getReviewedAt()).isNull();
        assertThat(request.getReviewNote()).isNull();
    }

    private long count(String query) {
        return em.createQuery(query, Long.class).setParameter("id", classId).getSingleResult();
    }

    private void assertFullClass(Throwable failure) {
        assertThat(failure).isInstanceOf(ResponseStatusException.class);
        ResponseStatusException response = (ResponseStatusException) failure;
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getReason()).isEqualTo("Lớp học đã đủ sĩ số");
    }

    private User persistUser(String username, Role role) {
        User user = User.builder().username(username).password("unused-test-password")
                .fullName(username).role(role).build();
        em.persist(user);
        return user;
    }

    private Student persistStudent(User parent, String name) {
        Student student = Student.builder().parent(parent).fullName(name).build();
        em.persist(student);
        return student;
    }

    private Long persistRequest(User parent, Course course, String name) {
        EnrollmentRequest request = EnrollmentRequest.builder()
                .student(persistStudent(parent, name)).requestedBy(parent).preferredCourse(course)
                .status(EnrollmentRequestStatus.READY_FOR_ASSIGNMENT).build();
        em.persist(request);
        return request.getId();
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out coordinating concurrent reviewers");
        }
    }
}
