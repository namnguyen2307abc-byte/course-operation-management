package com.talent.management.features.class_management.service;

import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.branch_facility_enrollment.repository.RoomRepository;
import com.talent.management.features.branch_facility_enrollment.repository.EquipmentRepository;
import com.talent.management.features.branch_facility_enrollment.service.FacilityRulesService;
import com.talent.management.features.class_management.repository.ManagedClassLessonRepository;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassSchedulingServiceTest {
    @Mock UserRepository userRepository;
    @Mock RoomRepository roomRepository;
    @Mock ManagedClassLessonRepository lessonRepository;
    @Mock EquipmentRepository equipmentRepository;
    private ClassSchedulingService service;
    private Course course;
    private Branch branch;
    private User teacher;
    private Room room;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        service = new ClassSchedulingService(userRepository, roomRepository, lessonRepository,
                new FacilityRulesService(equipmentRepository));
        lenient().when(equipmentRepository.findByRoomIdOrderByIdAsc(3L)).thenReturn(java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> Equipment.builder().category(EquipmentCategory.KEYBOARD).build()).toList());
        course = Course.builder().program(Program.builder().code("PIANO").build()).totalSessions(24).build();
        branch = Branch.builder().id(1L).build();
        teacher = User.builder().id(2L).role(Role.TEACHER).subject("Piano").build();
        room = Room.builder().id(3L).branch(branch).capacity(8).roomType(RoomType.PIANO_GROUP).build();
        date = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(10);
    }

    private Lesson lesson(long id, LocalDate day, int startHour, int startMinute, int endHour, int endMinute) {
        return Lesson.builder().id(id).teacher(teacher).room(room).lessonDate(day)
                .startTime(LocalTime.of(startHour, startMinute)).endTime(LocalTime.of(endHour, endMinute))
                .status(LessonStatus.SCHEDULED).classEntity(ClassEntity.builder().classCode("OTHER").build()).build();
    }

    private void teacherHas(Lesson existing) {
        when(lessonRepository.findByTeacherIdAndLessonDateBetween(eq(2L), any(), any()))
                .thenReturn(List.of(existing));
    }

    @Test
    void acceptsSpecificSubjectAliasesButNeverTreatsAllMusicTeachersAsInterchangeable() {
        for (String subject : List.of("PIANO", "Đàn Piano", "Bộ môn Piano")) {
            teacher.setSubject(subject);
            assertThat(service.isTeacherSuitable(course, teacher)).isTrue();
        }
        for (String subject : List.of("GUITAR", "Đàn", "", "MUSIC")) {
            teacher.setSubject(subject);
            assertThat(service.isTeacherSuitable(course, teacher)).isFalse();
        }
        course.setProgram(Program.builder().code("SWIMMING").build());
        teacher.setSubject("Bơi lội");
        assertThat(service.isTeacherSuitable(course, teacher)).isTrue();
    }

    @Test
    void rejectsInactiveTeacherAndWrongRole() {
        teacher.setStatus(UserStatus.INACTIVE);
        assertThat(service.isTeacherSuitable(course, teacher)).isFalse();
        teacher.setStatus(UserStatus.ACTIVE);
        teacher.setRole(Role.PARENT);
        assertThat(service.isTeacherSuitable(course, teacher)).isFalse();
    }

    @Test
    void classSpecialismDoesNotChangeTheExistingPlacementDepartment() {
        teacher.setSubject("DAN");
        teacher.setTeachingSubject("PIANO");
        assertThat(service.isTeacherSuitable(course, teacher)).isTrue();
        assertThat(teacher.getSubject()).isEqualTo("DAN");
        course.setProgram(Program.builder().code("GUITAR").build());
        assertThat(service.isTeacherSuitable(course, teacher)).isFalse();
    }

    @Test
    void validatesBranchCapacityRoomTypeAndMaintenance() {
        service.validateAssignments(course, branch, teacher, room, 8);
        assertThatThrownBy(() -> service.validateAssignments(course, branch, teacher, room, 9))
                .hasMessageContaining("sức chứa");
        room.setStatus(RoomStatus.MAINTENANCE);
        assertThat(service.isRoomSuitable(course, room, 8)).isFalse();
        room.setStatus(RoomStatus.AVAILABLE);
        room.setRoomType(RoomType.GUITAR_ROOM);
        assertThat(service.isRoomSuitable(course, room, 8)).isFalse();
        room.setRoomType(RoomType.PIANO_GROUP);
        room.setBranch(Branch.builder().id(99L).build());
        assertThatThrownBy(() -> service.validateAssignments(course, branch, teacher, room, 8))
                .hasMessageContaining("không thuộc cơ sở");
        branch.setActive(false);
        assertThatThrownBy(() -> service.validateAssignments(course, branch, teacher, null, 8))
                .hasMessageContaining("ngừng hoạt động");
    }

    @Test
    void swimmingNeedsSportsAreaInsteadOfAnOrdinaryMultipurposeRoom() {
        course.setProgram(Program.builder().code("SWIMMING").build());
        room.setRoomType(RoomType.MULTIPURPOSE_ROOM);
        assertThat(service.isRoomSuitable(course, room, 8)).isFalse();
        room.setRoomType(RoomType.SPORTS_AREA);
        assertThat(service.isRoomSuitable(course, room, 8)).isFalse();
        room.setRoomType(RoomType.SWIMMING_POOL);
        when(equipmentRepository.findByRoomIdOrderByIdAsc(3L)).thenReturn(List.of(
                Equipment.builder().category(EquipmentCategory.RESCUE_EQUIPMENT).build()));
        assertThat(service.isRoomSuitable(course, room, 8)).isTrue();
    }

    @Test
    void planningCanBeIncompleteButOpenAndInProgressCannot() {
        service.validateReadiness(ClassStatus.PLANNING, false, null, null);
        for (ClassStatus status : List.of(ClassStatus.OPEN, ClassStatus.IN_PROGRESS)) {
            assertThatThrownBy(() -> service.validateReadiness(status, true, null, room)).hasMessageContaining("Dự kiến");
            assertThatThrownBy(() -> service.validateReadiness(status, true, teacher, null)).hasMessageContaining("Dự kiến");
            assertThatThrownBy(() -> service.validateReadiness(status, false, teacher, room)).hasMessageContaining("Dự kiến");
            service.validateReadiness(status, true, teacher, room);
        }
    }

    @Test
    void rejectsOverlappingTeacherAndShortBreakOnEitherSide() {
        teacherHas(lesson(100, date, 17, 0, 19, 0));
        for (Lesson candidate : List.of(lesson(1, date, 18, 0, 20, 0), lesson(1, date, 19, 0, 21, 0),
                lesson(1, date, 19, 29, 21, 0), lesson(1, date, 15, 0, 16, 31))) {
            assertThatThrownBy(() -> service.validateLessonConflicts(List.of(candidate)))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("30 phút");
        }
    }

    @Test
    void allowsExactlyThirtyMinuteBreakBeforeOrAfterExistingLesson() {
        teacherHas(lesson(100, date, 17, 0, 19, 0));
        service.validateLessonConflicts(List.of(lesson(1, date, 19, 30, 21, 0)));
        service.validateLessonConflicts(List.of(lesson(1, date, 14, 30, 16, 30)));
    }

    @Test
    void checksTeacherAcrossBranchesAndAcrossMidnight() {
        Lesson existing = lesson(100, date, 22, 0, 23, 50);
        existing.setRoom(Room.builder().id(99L).build());
        teacherHas(existing);
        assertThatThrownBy(() -> service.validateLessonConflicts(List.of(lesson(1, date.plusDays(1), 0, 10, 1, 0))))
                .hasMessageContaining("30 phút");
        service.validateLessonConflicts(List.of(lesson(1, date.plusDays(1), 0, 20, 1, 0)));
    }

    @Test
    void checksEveryGeneratedDateNotOnlyTheFirstLesson() {
        teacherHas(lesson(100, date.plusWeeks(3), 17, 0, 19, 0));
        assertThatThrownBy(() -> service.validateLessonConflicts(List.of(
                lesson(1, date, 18, 0, 20, 0), lesson(2, date.plusWeeks(3), 18, 0, 20, 0))))
                .hasMessageContaining(date.plusWeeks(3).toString());
    }

    @Test
    void sameWeekdayInAnotherDateRangeDoesNotConflictAndCancelledLessonsDoNotBlock() {
        Lesson existing = lesson(100, date.plusWeeks(10), 17, 0, 19, 0);
        teacherHas(existing);
        service.validateLessonConflicts(List.of(lesson(1, date, 17, 0, 19, 0)));
        existing.setLessonDate(date);
        existing.setStatus(LessonStatus.CANCELLED);
        service.validateLessonConflicts(List.of(lesson(1, date, 17, 0, 19, 0)));
    }

    @Test
    void roomCannotOverlapButMayBeUsedImmediatelyAfterAnotherTeacherFinishes() {
        Lesson existing = lesson(100, date, 17, 0, 19, 0);
        existing.setTeacher(User.builder().id(99L).build());
        when(lessonRepository.findByRoomIdAndLessonDateBetween(eq(3L), any(), any())).thenReturn(List.of(existing));
        assertThatThrownBy(() -> service.validateLessonConflicts(List.of(lesson(1, date, 18, 0, 20, 0))))
                .hasMessageContaining("Phòng học bị trùng");
        service.validateLessonConflicts(List.of(lesson(1, date, 19, 0, 21, 0)));
    }

    @Test
    void excludesOnlyEditedLessonIdsAndStillChecksOtherLessonsOfTheSameClass() {
        Lesson candidate = lesson(1, date, 17, 0, 19, 0);
        teacherHas(candidate);
        service.validateLessonConflicts(List.of(candidate));
        teacherHas(lesson(100, date, 17, 0, 19, 0));
        assertThatThrownBy(() -> service.validateLessonConflicts(List.of(candidate))).hasMessageContaining("30 phút");
    }

    @Test
    void detectsConflictsBetweenLessonsBeingReassignedTogether() {
        assertThatThrownBy(() -> service.validateLessonConflicts(List.of(
                lesson(1, date, 17, 0, 19, 0), lesson(2, date, 19, 15, 21, 0))))
                .hasMessageContaining("30 phút");
    }

    @Test
    void generatesRequestedSessionsAndChecksTheCalculatedEndDate() {
        ClassEntity target = ClassEntity.builder().course(course).startDate(date).build();
        List<Lesson> lessons = service.generateLessons(target, List.of(date.getDayOfWeek().getValue()),
                LocalTime.of(9, 0), LocalTime.of(10, 0), 60, null);
        assertThat(lessons).hasSize(60);
        assertThat(target.getEndDate()).isEqualTo(date.plusWeeks(59));
        assertThatThrownBy(() -> service.generateLessons(target, List.of(date.getDayOfWeek().getValue()),
                LocalTime.of(9, 0), LocalTime.of(10, 0), 60, date.plusWeeks(2)))
                .hasMessageContaining("Ngày kết thúc");
        assertThat(service.generateLessons(target, List.of(date.getDayOfWeek().getValue()),
                LocalTime.of(9, 0), LocalTime.of(10, 0), null, null)).hasSize(24);
    }

    @Test
    void cannotGenerateLessonsInThePastOrChangePastAndCompletedAssignments() {
        ClassEntity target = ClassEntity.builder().course(course).startDate(date.minusYears(1)).build();
        assertThatThrownBy(() -> service.generateLessons(target, List.of(1), LocalTime.of(9, 0), LocalTime.of(10, 0), 1, null))
                .hasMessageContaining("quá khứ");
        assertThat(service.canReassign(lesson(1, date.minusYears(1), 9, 0, 10, 0))).isFalse();
        Lesson completed = lesson(1, date, 9, 0, 10, 0);
        completed.setStatus(LessonStatus.COMPLETED);
        assertThat(service.canReassign(completed)).isFalse();
        assertThat(service.canReassign(lesson(1, date, 9, 0, 10, 0))).isTrue();
    }
}
