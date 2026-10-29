package com.talent.management.features.branch_facility_enrollment.repository;

import com.talent.management.features.branch_facility_enrollment.service.FacilityRulesService;
import com.talent.management.features.class_management.repository.ManagedClassLessonRepository;
import com.talent.management.features.class_management.service.ClassSchedulingService;
import com.talent.management.shared.entity.Branch;
import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.Lesson;
import com.talent.management.shared.entity.Program;
import com.talent.management.shared.entity.Room;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.ClassStatus;
import com.talent.management.shared.enums.LessonStatus;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.enums.RoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(connection = EmbeddedDatabaseConnection.H2,
        replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({ClassSchedulingService.class, FacilityRulesService.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.show_sql=false",
        "spring.jpa.show-sql=false"
})
class FacilityUsagePersistenceTest {
    @Autowired TestEntityManager entityManager;
    @Autowired FacilityClassRepository classRepository;
    @Autowired FacilityLessonRepository lessonRepository;
    @Autowired ManagedClassLessonRepository schedulingRepository;
    @Autowired ClassSchedulingService schedulingService;

    private Branch branch;
    private Course course;
    private Room room;
    private Room otherRoom;
    private LocalDate today;
    private LocalTime now;

    @BeforeEach
    void setUp() {
        LocalDateTime current = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        today = current.toLocalDate();
        now = current.toLocalTime().withNano(0);
        Program program = entityManager.persist(Program.builder().code("GUITAR").name("Guitar").build());
        course = entityManager.persist(Course.builder().program(program).code("GUITAR-1").name("Guitar")
                .tuitionFee(BigDecimal.valueOf(100)).build());
        branch = entityManager.persist(Branch.builder().code("B1").name("Branch").address("Address").build());
        room = createRoom("R1");
        otherRoom = createRoom("R2");
    }

    @ParameterizedTest
    @EnumSource(value = ClassStatus.class, names = {"PLANNING", "OPEN", "IN_PROGRESS"})
    void includesNonfinishedDefaultClassesEvenWithoutRemainingLessons(ClassStatus status) {
        ClassEntity active = createClass("ACTIVE", room, status);
        ClassEntity history = createClass("HISTORY", room, ClassStatus.FINISHED);
        createClass("OTHER", otherRoom, status);
        createLesson(history, room, today.minusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0),
                LessonStatus.SCHEDULED);
        createLesson(history, room, today.plusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0),
                LessonStatus.COMPLETED);
        createLesson(history, room, today.plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0),
                LessonStatus.CANCELLED);
        flushAndClear();

        assertThat(classRepository.findClassesUsingRoom(room.getId(), today, now))
                .extracting(ClassEntity::getId).containsExactly(active.getId());
    }

    @ParameterizedTest
    @EnumSource(ClassStatus.class)
    void includesClassUsingActualLessonRoomWhenItsDefaultIsElsewhere(ClassStatus status) {
        ClassEntity visiting = createClass("VISITING", otherRoom, status);
        createLesson(visiting, room, today.plusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0),
                LessonStatus.SCHEDULED);
        createLesson(visiting, room, today.plusDays(2), LocalTime.of(9, 0), LocalTime.of(10, 0),
                LessonStatus.SCHEDULED);
        flushAndClear();

        assertThat(classRepository.findClassesUsingRoom(room.getId(), today, now))
                .extracting(ClassEntity::getId).containsExactly(visiting.getId());
    }

    @Test
    void finishedClassWithFutureFallbackLessonStillUsesItsDefaultRoom() {
        ClassEntity scheduled = createClass("STILL-SCHEDULED", room, ClassStatus.FINISHED);
        Lesson fallback = createLesson(scheduled, null, today.plusDays(1), LocalTime.of(9, 0),
                LocalTime.of(10, 0), LessonStatus.SCHEDULED);
        flushAndClear();

        assertThat(lessonRepository.findRemainingRoomLessons(room.getId(), today, now))
                .extracting(Lesson::getId).containsExactly(fallback.getId());
        assertThat(classRepository.findClassesUsingRoom(room.getId(), today, now))
                .extracting(ClassEntity::getId).containsExactly(scheduled.getId());
    }

    @Test
    void usesActualRoomOverrideAndFallsBackToClassRoomOnlyWhenLessonRoomIsNull() {
        ClassEntity home = createClass("HOME", room, ClassStatus.OPEN);
        ClassEntity visiting = createClass("VISITING", otherRoom, ClassStatus.OPEN);
        ClassEntity unassigned = createClass("UNASSIGNED", null, ClassStatus.PLANNING);
        LocalDate tomorrow = today.plusDays(1);
        Lesson actual = createLesson(visiting, room, tomorrow, LocalTime.of(9, 0), LocalTime.of(10, 0),
                LessonStatus.SCHEDULED);
        Lesson fallback = createLesson(home, null, tomorrow, LocalTime.of(10, 0), LocalTime.of(11, 0),
                LessonStatus.SCHEDULED);
        Lesson movedAway = createLesson(home, otherRoom, tomorrow, LocalTime.of(11, 0), LocalTime.NOON,
                LessonStatus.SCHEDULED);
        Lesson otherFallback = createLesson(visiting, null, tomorrow, LocalTime.NOON, LocalTime.of(13, 0),
                LessonStatus.SCHEDULED);
        createLesson(unassigned, null, tomorrow, LocalTime.NOON, LocalTime.of(13, 0), LessonStatus.SCHEDULED);
        flushAndClear();

        assertThat(lessonRepository.findRemainingRoomLessons(room.getId(), today, now))
                .extracting(Lesson::getId).containsExactly(actual.getId(), fallback.getId());
        assertThat(lessonRepository.findRemainingRoomLessons(otherRoom.getId(), today, now))
                .extracting(Lesson::getId).containsExactly(movedAway.getId(), otherFallback.getId());
        assertThat(schedulingRepository.findByRoomIdAndLessonDateBetween(room.getId(), tomorrow, tomorrow))
                .extracting(Lesson::getId).containsExactlyInAnyOrder(actual.getId(), fallback.getId());
        assertThat(schedulingRepository.findByRoomIdAndLessonDateBetween(otherRoom.getId(), tomorrow, tomorrow))
                .extracting(Lesson::getId).containsExactlyInAnyOrder(movedAway.getId(), otherFallback.getId());
    }

    @Test
    void includesOngoingScheduledLessonsAndOrdersThemBeforeFutureLessons() {
        ClassEntity visiting = createClass("VISITING", otherRoom, ClassStatus.FINISHED);
        Lesson future = createLesson(visiting, room, today.plusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0),
                LessonStatus.SCHEDULED);
        Lesson ongoing = createLesson(visiting, room, today, LocalTime.MIN, LocalTime.of(23, 59, 59),
                LessonStatus.SCHEDULED);
        flushAndClear();

        assertThat(lessonRepository.findRemainingRoomLessons(room.getId(), today, now))
                .extracting(Lesson::getId).containsExactly(ongoing.getId(), future.getId());
        assertThat(classRepository.findClassesUsingRoom(room.getId(), today, now))
                .extracting(ClassEntity::getId).containsExactly(visiting.getId());
    }

    @Test
    void ongoingLessonAloneKeepsItsClassInRoomUsage() {
        ClassEntity visiting = createClass("ONGOING", otherRoom, ClassStatus.FINISHED);
        createLesson(visiting, room, today, LocalTime.MIN, LocalTime.of(23, 59, 59), LessonStatus.SCHEDULED);
        flushAndClear();

        assertThat(classRepository.findClassesUsingRoom(room.getId(), today, now))
                .extracting(ClassEntity::getId).containsExactly(visiting.getId());
    }

    @Test
    void excludesCompletedCancelledPastAndExactlyEndedLessonsFromBothQueries() {
        ClassEntity history = createClass("HISTORY", otherRoom, ClassStatus.FINISHED);
        createLesson(history, room, today.plusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0),
                LessonStatus.COMPLETED);
        createLesson(history, room, today.plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0),
                LessonStatus.CANCELLED);
        createLesson(history, room, today.minusDays(1), LocalTime.MIN, LocalTime.of(23, 59, 59),
                LessonStatus.SCHEDULED);
        createLesson(history, room, today, LocalTime.MIN, now, LessonStatus.SCHEDULED);
        flushAndClear();

        assertThat(lessonRepository.findRemainingRoomLessons(room.getId(), today, now)).isEmpty();
        assertThat(classRepository.findClassesUsingRoom(room.getId(), today, now)).isEmpty();
    }

    @Test
    void teacherConflictQueryUsesOverridesAndFallbackWithinInclusiveDates() {
        User teacher = createTeacher("teacher");
        User otherTeacher = createTeacher("other-teacher");
        ClassEntity home = createClass("HOME", room, ClassStatus.OPEN);
        home.setTeacher(teacher);
        ClassEntity visiting = createClass("VISITING", otherRoom, ClassStatus.OPEN);
        visiting.setTeacher(otherTeacher);
        ClassEntity unassigned = createClass("UNASSIGNED", null, ClassStatus.PLANNING);
        LocalDate from = today.plusDays(1);
        LocalDate to = from.plusDays(1);
        Lesson fallback = createLesson(home, null, from, LocalTime.of(9, 0), LocalTime.of(10, 0),
                LessonStatus.SCHEDULED);
        Lesson actual = createLesson(visiting, otherRoom, to, LocalTime.of(9, 0), LocalTime.of(10, 0),
                LessonStatus.SCHEDULED);
        actual.setTeacher(teacher);
        Lesson movedAway = createLesson(home, room, from, LocalTime.of(11, 0), LocalTime.NOON,
                LessonStatus.SCHEDULED);
        movedAway.setTeacher(otherTeacher);
        createLesson(home, null, from.minusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0),
                LessonStatus.SCHEDULED);
        createLesson(home, null, to.plusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0),
                LessonStatus.SCHEDULED);
        createLesson(unassigned, null, from, LocalTime.of(9, 0), LocalTime.of(10, 0), LessonStatus.SCHEDULED);
        flushAndClear();

        assertThat(schedulingRepository.findByTeacherIdAndLessonDateBetween(teacher.getId(), from, to))
                .extracting(Lesson::getId).containsExactlyInAnyOrder(fallback.getId(), actual.getId());
        assertThat(schedulingRepository.findByTeacherIdAndLessonDateBetween(otherTeacher.getId(), from, to))
                .extracting(Lesson::getId).containsExactly(movedAway.getId());
    }

    @Test
    void inheritedTeacherRequiresThirtyMinuteBreakEvenInAnotherRoom() {
        User teacher = createTeacher("teacher");
        ClassEntity existing = createClass("EXISTING", otherRoom, ClassStatus.OPEN);
        existing.setTeacher(teacher);
        LocalDate day = today.plusDays(1);
        createLesson(existing, null, day, LocalTime.of(17, 0), LocalTime.of(18, 0), LessonStatus.SCHEDULED);
        flushAndClear();
        Lesson candidate = Lesson.builder().teacher(teacher).room(room).lessonDate(day)
                .startTime(LocalTime.of(18, 29)).endTime(LocalTime.of(19, 30)).build();

        assertThatThrownBy(() -> schedulingService.validateLessonConflicts(List.of(candidate)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("30");
        candidate.setStartTime(LocalTime.of(18, 30));
        schedulingService.validateLessonConflicts(List.of(candidate));
    }

    @Test
    void inheritedRoomBlocksOverlapButAllowsImmediateHandoverToAnotherTeacher() {
        User teacher = createTeacher("teacher");
        User otherTeacher = createTeacher("other-teacher");
        ClassEntity existing = createClass("EXISTING", room, ClassStatus.OPEN);
        existing.setTeacher(teacher);
        LocalDate day = today.plusDays(1);
        createLesson(existing, null, day, LocalTime.of(17, 0), LocalTime.of(19, 0), LessonStatus.SCHEDULED);
        flushAndClear();
        Lesson candidate = Lesson.builder().teacher(otherTeacher).room(room).lessonDate(day)
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(20, 0)).build();

        assertThatThrownBy(() -> schedulingService.validateLessonConflicts(List.of(candidate)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("EXISTING");
        candidate.setStartTime(LocalTime.of(19, 0));
        schedulingService.validateLessonConflicts(List.of(candidate));
    }

    @Test
    void explicitLessonOverridesFreeTheClassDefaultRoomAndTeacher() {
        User teacher = createTeacher("teacher");
        User otherTeacher = createTeacher("other-teacher");
        ClassEntity existing = createClass("EXISTING", room, ClassStatus.OPEN);
        existing.setTeacher(teacher);
        LocalDate day = today.plusDays(1);
        Lesson movedAway = createLesson(existing, otherRoom, day, LocalTime.of(17, 0), LocalTime.of(19, 0),
                LessonStatus.SCHEDULED);
        movedAway.setTeacher(otherTeacher);
        flushAndClear();
        Lesson candidate = Lesson.builder().teacher(teacher).room(room).lessonDate(day)
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(20, 0)).build();

        schedulingService.validateLessonConflicts(List.of(candidate));
    }

    private User createTeacher(String username) {
        return entityManager.persist(User.builder().username(username).password("test")
                .fullName(username).role(Role.TEACHER).subject("GUITAR").build());
    }

    private Room createRoom(String code) {
        return entityManager.persist(Room.builder().branch(branch).roomCode(code).roomName(code)
                .capacity(20).roomType(RoomType.GUITAR_ROOM).build());
    }

    private ClassEntity createClass(String code, Room defaultRoom, ClassStatus status) {
        return entityManager.persist(ClassEntity.builder().course(course).branch(branch).room(defaultRoom)
                .classCode(code).className(code).maxStudents(10).status(status)
                .startDate(today.minusDays(10)).endDate(today.plusDays(10)).build());
    }

    private Lesson createLesson(ClassEntity classEntity, Room actualRoom, LocalDate date,
                                LocalTime start, LocalTime end, LessonStatus status) {
        return entityManager.persist(Lesson.builder().classEntity(classEntity).room(actualRoom)
                .lessonDate(date).startTime(start).endTime(end).status(status).build());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
