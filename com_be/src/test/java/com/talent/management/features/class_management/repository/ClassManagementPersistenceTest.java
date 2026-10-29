package com.talent.management.features.class_management.repository;

import com.talent.management.features.course_enrollment.repository.ClassRepository;
import com.talent.management.shared.entity.Branch;
import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.Program;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.entity.Room;
import com.talent.management.shared.entity.Equipment;
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
@Import({ClassManagementService.class, ClassSchedulingService.class, FacilityRulesService.class})
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

    @Test
    void planningClassAppearsForEnrollmentOnlyAfterOpeningAndDisappearsWhenClosed() {
        Program program = entityManager.persist(Program.builder().code("PIANO").name("Piano").build());
        Course course = entityManager.persist(Course.builder().program(program).code("PIA-G1")
                .name("Piano Grade 1").tuitionFee(BigDecimal.valueOf(4800000)).build());
        Branch branch = entityManager.persist(Branch.builder().code("BR-01").name("Cơ sở A").address("Hà Nội").build());
        var created = service.create(new ClassUpsertRequest(course.getId(), branch.getId(), "QA-CLASS-01",
                "Lớp kiểm thử", 3, "PLANNING", LocalDate.of(2026, 11, 1), LocalDate.of(2027, 2, 1)));
        assertThat(enrollmentRepository.findByCourseIdAndStatusOrderByStartDateAsc(course.getId(), ClassStatus.OPEN)).isEmpty();
        User teacher = entityManager.persist(User.builder().username("piano-teacher").password("test")
                .fullName("Teacher").role(Role.TEACHER).subject("PIANO").build());
        Room room = entityManager.persist(Room.builder().branch(branch).roomCode("R1").roomName("Piano")
                .capacity(8).roomType(RoomType.PIANO_GROUP).build());
        addPianos(room, 3);
        created = service.update(created.id(), new ClassUpsertRequest(course.getId(), branch.getId(),
                teacher.getId(), room.getId(), created.classCode(), "Lớp kiểm thử", 3, "OPEN", created.startDate(), null,
                null, List.of(7), LocalTime.of(9, 0), LocalTime.of(10, 0), 4));
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
                null, List.of(first.startDate().getDayOfWeek().getValue()), LocalTime.of(19, 29), LocalTime.of(21, 0), 2)))
                .hasMessageContaining("30 phút");
    }

    @Test
    void persistedScheduleAllowsExactlyThirtyMinutesBreakAndDoesNotConflictWithItselfOnUpdate() {
        var first = createScheduledClass("FIRST", LocalTime.of(17, 0), LocalTime.of(19, 0));
        var second = service.create(new ClassUpsertRequest(first.courseId(), first.branchId(),
                first.teacherId(), first.roomId(), "SECOND", "Second", 4, "OPEN", first.startDate(), null,
                null, List.of(first.startDate().getDayOfWeek().getValue()), LocalTime.of(19, 30), LocalTime.of(21, 0), 2));
        entityManager.flush();
        assertThat(second.totalLessons()).isEqualTo(2);
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
                null, List.of(first.startDate().getDayOfWeek().getValue()), LocalTime.of(18, 0), LocalTime.of(20, 0), 2)))
                .hasMessageContaining("Phòng học bị trùng");
    }

    private com.talent.management.features.class_management.dto.ManagedClassResponse createScheduledClass(
            String code, LocalTime startTime, LocalTime endTime) {
        Program program = entityManager.persist(Program.builder().code("PIANO").name("Piano").build());
        Course course = entityManager.persist(Course.builder().program(program).code("PIANO-1").name("Piano")
                .tuitionFee(BigDecimal.valueOf(100)).build());
        Branch branch = entityManager.persist(Branch.builder().code("B1").name("Branch").address("Address").build());
        User teacher = entityManager.persist(User.builder().username("teacher").password("test").fullName("Teacher")
                .role(Role.TEACHER).subject("PIANO").build());
        Room room = entityManager.persist(Room.builder().branch(branch).roomCode("R1").roomName("Piano")
                .capacity(8).roomType(RoomType.PIANO_GROUP).build());
        addPianos(room, 4);
        LocalDate start = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(10);
        return service.create(new ClassUpsertRequest(course.getId(), branch.getId(), teacher.getId(), room.getId(),
                code, code, 4, "OPEN", start, null, null,
                List.of(start.getDayOfWeek().getValue()), startTime, endTime, 2));
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
                .name("Piano Grade 1").tuitionFee(BigDecimal.valueOf(4800000)).build());
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
