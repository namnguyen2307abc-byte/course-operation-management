package com.talent.management.features.class_management.repository;

import com.talent.management.features.course_enrollment.repository.ClassRepository;
import com.talent.management.shared.entity.Branch;
import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.Program;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.entity.Room;
import com.talent.management.shared.entity.Equipment;
import com.talent.management.shared.entity.Student;
import com.talent.management.shared.entity.Enrollment;
import com.talent.management.shared.entity.Invoice;
import com.talent.management.features.course_management.service.impl.CourseManagementServiceImpl;
import com.talent.management.features.course_management.dto.request.UpdateCourseRequest;
import com.talent.management.features.placement_test.entity.PlacementLevel;
import com.talent.management.shared.enums.EquipmentCategory;
import com.talent.management.features.branch_facility_enrollment.service.FacilityRulesService;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.enums.RoomType;
import com.talent.management.shared.enums.ClassStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.context.annotation.Import;
import com.talent.management.features.class_management.service.ClassManagementService;
import com.talent.management.features.class_management.service.ClassSchedulingService;
import com.talent.management.features.class_management.dto.ClassUpsertRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({ClassManagementService.class, ClassSchedulingService.class, FacilityRulesService.class, CourseManagementServiceImpl.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.show_sql=false",
        "spring.jpa.show-sql=false"
})
class ClassManagementPersistenceTest {
    @Autowired TestEntityManager entityManager;
    @Autowired ManagedClassRepository managementRepository;
    @Autowired ClassRepository enrollmentRepository;
    @Autowired ClassManagementService service;
    @Autowired CourseManagementServiceImpl courseService;

    @Test
    void courseRepricingDoesNotRewriteExistingLessonsOrInvoice() {
        var created = createScheduledClass("FIRST", LocalTime.of(9, 0), LocalTime.of(11, 0));
        Student student = entityManager.persist(Student.builder().fullName("Nguyễn An").build());
        Enrollment enrollment = entityManager.persist(Enrollment.builder().student(student)
                .classEntity(entityManager.find(ClassEntity.class, created.id())).build());
        Invoice invoice = entityManager.persistAndFlush(Invoice.builder().invoiceCode("INV-EXISTING")
                .student(student).enrollment(enrollment).originalAmount(new BigDecimal("2400000"))
                .finalAmount(new BigDecimal("2400000")).build());
        Course course = entityManager.find(Course.class, created.courseId());
        courseService.updateCourse(course.getId(), UpdateCourseRequest.builder()
                .programId(course.getProgram().getId()).code(course.getCode()).name(course.getName())
                .level(PlacementLevel.BEGINNER).totalSessions(new BigDecimal("24"))
                .tuitionPerSession(new BigDecimal("200000")).build());
        entityManager.flush();
        entityManager.clear();
        Course saved = entityManager.find(Course.class, created.courseId());
        assertThat(saved.getTuitionPerSession()).isEqualByComparingTo("200000");
        assertThat(saved.getTuitionFee()).isEqualByComparingTo("4800000");
        assertThat(entityManager.find(Invoice.class, invoice.getId()).getFinalAmount()).isEqualByComparingTo("2400000");
        assertThat(entityManager.find(ClassEntity.class, created.id()).getEndDate()).isEqualTo(created.endDate());
        assertThat(entityManager.getEntityManager().createQuery(
                "select count(l) from Lesson l where l.classEntity.id = :id", Long.class)
                .setParameter("id", created.id()).getSingleResult()).isEqualTo(12);
        // Old schedules remain editable even if their course now defines a different number of lessons.
        service.update(created.id(), new ClassUpsertRequest(created.courseId(), created.branchId(),
                created.teacherId(), created.roomId(), "FIRST", "Renamed", 4, "OPEN",
                created.startDate(), created.endDate(), null, null, null, null, null));
    }

    @Test
    void planningClassAppearsForEnrollmentOnlyAfterOpeningAndDisappearsWhenClosed() {
        Program program = entityManager.persist(Program.builder().code("PIANO").name("Piano").build());
        Course course = entityManager.persist(Course.builder().program(program).code("PIA-G1")
                .name("Piano Grade 1").totalSessions(12).tuitionFee(BigDecimal.valueOf(4800000)).build());
        Branch branch = entityManager.persist(Branch.builder().code("BR-01").name("Cơ sở A").address("Hà Nội").build());
        LocalDate start = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1);
        var created = service.create(new ClassUpsertRequest(course.getId(), branch.getId(), "QA-CLASS-01",
                "Lớp kiểm thử", 3, "PLANNING", start, start.plusMonths(3)));
        assertThat(enrollmentRepository.findByCourseIdAndStatusOrderByStartDateAsc(course.getId(), ClassStatus.OPEN)).isEmpty();
        User teacher = entityManager.persist(User.builder().username("piano-teacher").password("test")
                .fullName("Teacher").role(Role.TEACHER).subject("PIANO").build());
        Room room = entityManager.persist(Room.builder().branch(branch).roomCode("R1").roomName("Piano")
                .capacity(8).roomType(RoomType.PIANO_GROUP).build());
        addPianos(room, 3);
        created = service.update(created.id(), new ClassUpsertRequest(course.getId(), branch.getId(),
                teacher.getId(), room.getId(), created.classCode(), "Lớp kiểm thử", 3, "OPEN", created.startDate(), null,
                null, List.of(7), LocalTime.of(9, 0), LocalTime.of(10, 0), 12));
        final Long createdId = created.id();
        for (String status : new String[]{"OPEN", "IN_PROGRESS", "FINISHED"}) {
            service.update(created.id(), new ClassUpsertRequest(course.getId(), branch.getId(), created.classCode(),
                    "Lớp kiểm thử", 3, status, created.startDate(), created.endDate()));
            entityManager.flush(); entityManager.clear();
            var selectable = enrollmentRepository.findByCourseIdAndStatusOrderByStartDateAsc(course.getId(), ClassStatus.OPEN);
            if (status.equals("OPEN")) assertThat(selectable).extracting(ClassEntity::getId).containsExactly(createdId);
            else assertThat(selectable).isEmpty();
        }
    }

    @Test
    void persistedScheduleBlocksAnotherClassWithLessThanThirtyMinutesBreak() {
        var first = createScheduledClass("FIRST", LocalTime.of(17, 0), LocalTime.of(19, 0));
        entityManager.flush();
        assertThatThrownBy(() -> service.create(new ClassUpsertRequest(first.courseId(), first.branchId(),
                first.teacherId(), first.roomId(), "CONFLICT", "Conflict", 4, "OPEN", first.startDate(), null,
                null, List.of(first.startDate().getDayOfWeek().getValue()), LocalTime.of(19, 29), LocalTime.of(21, 0), 12)))
                .hasMessageContaining("30 phút");
    }

    @Test
    void persistedScheduleAllowsExactlyThirtyMinutesBreakAndDoesNotConflictWithItselfOnUpdate() {
        var first = createScheduledClass("FIRST", LocalTime.of(17, 0), LocalTime.of(19, 0));
        var second = service.create(new ClassUpsertRequest(first.courseId(), first.branchId(),
                first.teacherId(), first.roomId(), "SECOND", "Second", 4, "OPEN", first.startDate(), null,
                null, List.of(first.startDate().getDayOfWeek().getValue()), LocalTime.of(19, 30), LocalTime.of(21, 0), 12));
        entityManager.flush();
        assertThat(second.totalLessons()).isEqualTo(12);
        assertThat(service.update(first.id(), new ClassUpsertRequest(first.courseId(), first.branchId(),
                first.teacherId(), first.roomId(), "FIRST", "Renamed", 4, "OPEN", first.startDate(), first.endDate(),
                null, null, null, null, null)).className()).isEqualTo("Renamed");
    }

    @Test
    void persistedRoomScheduleBlocksAnotherTeacherAtTheSameTime() {
        var first = createScheduledClass("FIRST", LocalTime.of(17, 0), LocalTime.of(19, 0));
        User other = entityManager.persist(User.builder().username("other").password("test").fullName("Other")
                .role(Role.TEACHER).subject("PIANO").build());
        assertThatThrownBy(() -> service.create(new ClassUpsertRequest(first.courseId(), first.branchId(),
                other.getId(), first.roomId(), "SECOND", "Second", 4, "OPEN", first.startDate(), null,
                null, List.of(first.startDate().getDayOfWeek().getValue()), LocalTime.of(18, 0), LocalTime.of(20, 0), 12)))
                .hasMessageContaining("Phòng học bị trùng");
    }

    private com.talent.management.features.class_management.dto.ManagedClassResponse createScheduledClass(
            String code, LocalTime startTime, LocalTime endTime) {
        Program program = entityManager.persist(Program.builder().code("PIANO").name("Piano").build());
        Course course = entityManager.persist(Course.builder().program(program).code("PIANO-1").name("Piano")
                .totalSessions(12).tuitionFee(BigDecimal.valueOf(100)).build());
        Branch branch = entityManager.persist(Branch.builder().code("B1").name("Branch").address("Address").build());
        User teacher = entityManager.persist(User.builder().username("teacher").password("test").fullName("Teacher")
                .role(Role.TEACHER).subject("PIANO").build());
        Room room = entityManager.persist(Room.builder().branch(branch).roomCode("R1").roomName("Piano")
                .capacity(8).roomType(RoomType.PIANO_GROUP).build());
        addPianos(room, 4);
        LocalDate start = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(10);
        return service.create(new ClassUpsertRequest(course.getId(), branch.getId(), teacher.getId(), room.getId(),
                code, code, 4, "OPEN", start, null, null,
                List.of(start.getDayOfWeek().getValue()), startTime, endTime, 12));
    }

    private void addPianos(Room room, int count) {
        for (int i = 0; i < count; i++) {
            entityManager.persist(Equipment.builder().room(room).name("Keyboard " + i)
                    .category(EquipmentCategory.KEYBOARD).build());
        }
        entityManager.flush();
    }

    @Test
    void newOpenClassIsVisibleToEnrollmentRepository() {
        Program program = entityManager.persist(Program.builder().code("PIANO").name("Piano").build());
        Course course = entityManager.persist(Course.builder().program(program).code("PIA-G1")
                .name("Piano Grade 1").totalSessions(12).tuitionFee(BigDecimal.valueOf(4800000)).build());
        Branch branch = entityManager.persist(Branch.builder().code("BR-01").name("Cơ sở A")
                .address("Hà Nội").build());
        ClassEntity classEntity = entityManager.persistAndFlush(ClassEntity.builder()
                .course(course).branch(branch).classCode("PIA-G1-02").className("Piano Grade 1 - Sáng")
                .maxStudents(8).currentStudents(0).status(ClassStatus.OPEN)
                .startDate(LocalDate.of(2026, 11, 1)).endDate(LocalDate.of(2027, 2, 1)).build());
        entityManager.clear();

        assertThat(managementRepository.search("g1-02", course.getId(), branch.getId(), ClassStatus.OPEN))
                .extracting(ClassEntity::getId).containsExactly(classEntity.getId());
        assertThat(managementRepository.search("", null, null, null))
                .extracting(ClassEntity::getId).containsExactly(classEntity.getId());
        assertThat(enrollmentRepository.findByCourseIdAndStatusOrderByStartDateAsc(course.getId(), ClassStatus.OPEN))
                .extracting(ClassEntity::getId).containsExactly(classEntity.getId());
    }
}
