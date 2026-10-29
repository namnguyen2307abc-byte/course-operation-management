package com.talent.management.features.class_management.service;

import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.branch_facility_enrollment.repository.BranchRepository;
import com.talent.management.features.branch_facility_enrollment.repository.RoomRepository;
import com.talent.management.features.branch_facility_enrollment.repository.EquipmentRepository;
import com.talent.management.features.branch_facility_enrollment.service.FacilityRulesService;
import com.talent.management.shared.entity.Equipment;
import com.talent.management.shared.enums.EquipmentCategory;
import com.talent.management.features.class_management.dto.ClassUpsertRequest;
import com.talent.management.features.class_management.repository.ManagedClassEnrollmentRepository;
import com.talent.management.features.class_management.repository.ManagedClassLessonRepository;
import com.talent.management.features.class_management.repository.ManagedClassRepository;
import com.talent.management.features.course_enrollment.repository.CourseRepository;
import com.talent.management.shared.entity.Branch;
import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.Room;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.entity.Lesson;
import com.talent.management.shared.entity.Program;
import com.talent.management.shared.enums.RoomType;
import com.talent.management.shared.enums.LessonStatus;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.enums.ClassStatus;
import com.talent.management.shared.enums.EnrollmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassManagementServiceTest {
    @Mock ManagedClassRepository classRepository;
    @Mock ManagedClassEnrollmentRepository enrollmentRepository;
    @Mock ManagedClassLessonRepository lessonRepository;
    @Mock CourseRepository courseRepository;
    @Mock BranchRepository branchRepository;
    @Mock UserRepository userRepository;
    @Mock RoomRepository roomRepository;
    @Mock EquipmentRepository equipmentRepository;
    private ClassManagementService service;

    private Course course;
    private Branch branch;
    private ClassUpsertRequest request;


    @BeforeEach
    void setUp() {
        course = Course.builder().id(1L).code("PIA-G1").name("Piano Grade 1")
                .program(Program.builder().code("PIANO").build()).build();
        branch = Branch.builder().id(2L).name("Cơ sở A").build();
        ClassSchedulingService scheduling = new ClassSchedulingService(userRepository, roomRepository, lessonRepository,
                new FacilityRulesService(equipmentRepository));
        lenient().when(equipmentRepository.findByRoomIdOrderByIdAsc(20L)).thenReturn(java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> Equipment.builder().category(EquipmentCategory.KEYBOARD).build()).toList());
        service = new ClassManagementService(classRepository, enrollmentRepository, lessonRepository,
                courseRepository, branchRepository, userRepository, roomRepository, scheduling);
        lenient().when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        lenient().when(branchRepository.findById(2L)).thenReturn(Optional.of(branch));
        lenient().when(userRepository.findByIdForScheduleUpdate(30L)).thenReturn(Optional.of(teacher(30L)));
        lenient().when(roomRepository.findByIdForScheduleUpdate(20L)).thenReturn(Optional.of(room()));
        lenient().when(lessonRepository.existsByClassEntityId(10L)).thenReturn(true);
        request = new ClassUpsertRequest(1L, 2L, " pia-g1-02 ", " Lớp Piano mới ", 8,
                "PLANNING", LocalDate.of(2026, 11, 1), LocalDate.of(2027, 2, 1));
    }

    @Test
    void createsClassWithSameCourseAndBranchUsedByEnrollment() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(branchRepository.findById(2L)).thenReturn(Optional.of(branch));
        when(classRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ClassEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return entity;
        });

        var result = service.create(request);

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.courseId()).isEqualTo(1L);
        assertThat(result.branchId()).isEqualTo(2L);
        assertThat(result.classCode()).isEqualTo("PIA-G1-02");
        assertThat(result.currentStudents()).isZero();
        assertThat(result.availableSeats()).isEqualTo(8L);
        assertThat(result.status()).isEqualTo(ClassStatus.PLANNING);
    }

    @Test
    void rejectsDuplicateCodeIgnoringCase() {
        when(classRepository.existsByClassCodeIgnoreCase("PIA-G1-02")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(classRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidDates() {
        var invalid = new ClassUpsertRequest(1L, 2L, "C1", "Lớp", 8, "OPEN",
                LocalDate.of(2027, 2, 1), LocalDate.of(2026, 11, 1));
        assertThatThrownBy(() -> service.create(invalid))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(classRepository);
    }

    @Test
    void cannotReduceCapacityBelowPaidAndPendingStudents() {
        ClassEntity existing = existingClass();
        existing.setCurrentStudents(5);
        when(classRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(existing));
        when(enrollmentRepository.countByClassEntityIdAndStatus(10L, EnrollmentStatus.PENDING_PAYMENT)).thenReturn(2L);
        var smaller = new ClassUpsertRequest(1L, 2L, "PIA-G1-02", "Lớp Piano", 6, "OPEN",
                request.startDate(), request.endDate());

        assertThatThrownBy(() -> service.update(10L, smaller))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(classRepository, never()).saveAndFlush(any());
    }

    @Test
    void cannotChangeCourseAfterEnrollment() {
        when(classRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(existingClass()));
        when(enrollmentRepository.existsByClassEntityId(10L)).thenReturn(true);
        var changed = new ClassUpsertRequest(3L, 2L, "PIA-G1-02", "Lớp Piano", 8, "OPEN",
                request.startDate(), request.endDate());

        assertThatThrownBy(() -> service.update(10L, changed))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(classRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatingBasicFieldsPreservesLessonTeamAssignments() {
        ClassEntity existing = existingClass();
        Room room = room();
        User teacher = teacher(30L);
        when(roomRepository.findByIdForScheduleUpdate(20L)).thenReturn(Optional.of(room));
        when(userRepository.findByIdForScheduleUpdate(30L)).thenReturn(Optional.of(teacher));
        existing.setRoom(room);
        existing.setTeacher(teacher);
        existing.setScheduleDescription("Thứ 2 hàng tuần");
        when(classRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(existing));
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(branchRepository.findById(2L)).thenReturn(Optional.of(branch));
        when(classRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var changed = new ClassUpsertRequest(1L, 2L, "PIA-G1-02", "Tên mới", 8, "IN_PROGRESS",
                request.startDate(), request.endDate());

        service.update(10L, changed);

        ArgumentCaptor<ClassEntity> saved = ArgumentCaptor.forClass(ClassEntity.class);
        verify(classRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRoom()).isSameAs(room);
        assertThat(saved.getValue().getTeacher()).isSameAs(teacher);
        assertThat(saved.getValue().getScheduleDescription()).isEqualTo("Thứ 2 hàng tuần");
        assertThat(saved.getValue().getStatus()).isEqualTo(ClassStatus.IN_PROGRESS);
    }

    private ClassEntity existingClass() {
        return ClassEntity.builder().id(10L).course(course).branch(branch)
                .teacher(teacher(30L)).room(room())
                .classCode("PIA-G1-02").className("Lớp Piano")
                .maxStudents(8).currentStudents(0).status(ClassStatus.OPEN)
                .startDate(request.startDate()).endDate(request.endDate()).build();
    }

    private User teacher(long id) {
        return User.builder().id(id).role(Role.TEACHER).subject("PIANO").build();
    }

    private Room room() {
        return Room.builder().id(20L).branch(branch).roomType(RoomType.PIANO_GROUP).capacity(10).build();
    }

    private ClassUpsertRequest withSchedule(List<Integer> days, LocalTime start, LocalTime end, int count) {
        return new ClassUpsertRequest(1L, 2L, null, null, "C-SCHEDULE", "Lớp lịch", 8, "PLANNING",
                LocalDate.of(2026, 11, 1), null, null, days, start, end, count);
    }

    @Test
    void rejectsInvalidScheduleBeforeCreatingClassOrLessons() {
        for (var invalid : List.of(
                withSchedule(List.of(0), LocalTime.of(9, 0), LocalTime.of(10, 0), 4),
                withSchedule(List.of(1), LocalTime.of(10, 0), LocalTime.of(9, 0), 4),
                withSchedule(List.of(1), null, LocalTime.of(10, 0), 4),
                withSchedule(List.of(1), LocalTime.of(9, 0), LocalTime.of(10, 0), 61))) {
            assertThatThrownBy(() -> service.create(invalid)).isInstanceOf(ResponseStatusException.class);
        }
        verify(classRepository, never()).saveAndFlush(any());
        verify(lessonRepository, never()).saveAll(any());
    }

    @Test
    void generatesAllSixtySessionsForOneLessonPerWeek() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(branchRepository.findById(2L)).thenReturn(Optional.of(branch));
        when(classRepository.saveAndFlush(any())).thenAnswer(inv -> {
            ClassEntity entity = inv.getArgument(0); entity.setId(10L); return entity;
        });
        var result = service.create(withSchedule(List.of(7), LocalTime.of(9, 0), LocalTime.of(10, 0), 60));
        verify(lessonRepository).saveAll(argThat(items -> {
            java.util.ArrayList<Lesson> saved = new java.util.ArrayList<>(); items.forEach(saved::add);
            return saved.size() == 60 && saved.get(59).getLessonDate().equals(LocalDate.of(2026, 11, 1).plusWeeks(59));
        }));
        assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 11, 1).plusWeeks(59));
    }

    @Test
    void rejectsMissingTeacherInsteadOfSilentlyRemovingAssignment() {
        when(branchRepository.findById(2L)).thenReturn(Optional.of(branch));
        var input = new ClassUpsertRequest(1L, 2L, 99L, null, "C1", "Lớp", 8, "OPEN",
                request.startDate(), request.endDate(), null, null, null, null, null);
        assertThatThrownBy(() -> service.create(input)).isInstanceOf(ResponseStatusException.class);
        verify(classRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsNonTeacherAccountAndRoomFromAnotherBranch() {
        when(branchRepository.findById(2L)).thenReturn(Optional.of(branch));
        when(userRepository.findByIdForScheduleUpdate(99L)).thenReturn(Optional.of(User.builder().id(99L).role(Role.PARENT).build()));
        var wrongTeacher = new ClassUpsertRequest(1L, 2L, 99L, null, "C1", "Lớp", 8, "OPEN",
                request.startDate(), request.endDate(), null, null, null, null, null);
        assertThatThrownBy(() -> service.create(wrongTeacher)).isInstanceOf(ResponseStatusException.class);
        when(roomRepository.findByIdForScheduleUpdate(20L)).thenReturn(Optional.of(Room.builder().id(20L)
                .branch(Branch.builder().id(3L).build()).build()));
        var wrongRoom = new ClassUpsertRequest(1L, 2L, null, 20L, "C1", "Lớp", 8, "OPEN",
                request.startDate(), request.endDate(), null, null, null, null, null);
        assertThatThrownBy(() -> service.create(wrongRoom)).isInstanceOf(ResponseStatusException.class);
        verify(classRepository, never()).saveAndFlush(any());
    }

    @Test
    void editingMetadataPreservesPastAndCompletedLessonAssignments() {
        var existing = existingClass();
        User oldTeacher = teacher(30L);
        User newTeacher = teacher(31L);
        existing.setTeacher(oldTeacher);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        Lesson past = Lesson.builder().lessonDate(today.minusDays(1)).startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0)).teacher(oldTeacher).status(LessonStatus.SCHEDULED).build();
        Lesson completed = Lesson.builder().lessonDate(today).teacher(oldTeacher).status(LessonStatus.COMPLETED).build();
        Lesson upcoming = Lesson.builder().lessonDate(today.plusDays(1)).startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0)).teacher(oldTeacher).status(LessonStatus.SCHEDULED).build();
        when(classRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(existing));
        when(lessonRepository.existsByClassEntityId(10L)).thenReturn(true);
        when(lessonRepository.findByClassEntityId(10L)).thenReturn(List.of(past, completed, upcoming));
        when(userRepository.findByIdForScheduleUpdate(31L)).thenReturn(Optional.of(newTeacher));
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(branchRepository.findById(2L)).thenReturn(Optional.of(branch));
        when(classRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        service.update(10L, new ClassUpsertRequest(1L, 2L, 31L, null, "PIA-G1-02", "Tên mới", 8, "OPEN",
                request.startDate(), request.endDate(), null, null, null, null, null));
        assertThat(past.getTeacher()).isSameAs(oldTeacher);
        assertThat(completed.getTeacher()).isSameAs(oldTeacher);
        assertThat(upcoming.getTeacher()).isSameAs(newTeacher);
    }

    @Test
    void rejectsCalendarChangeWhenClassAlreadyHasLessons() {
        when(classRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(existingClass()));
        when(lessonRepository.existsByClassEntityId(10L)).thenReturn(true);
        var changed = new ClassUpsertRequest(1L, 2L, "PIA-G1-02", "Lớp", 8, "OPEN",
                request.startDate().plusDays(1), request.endDate());
        assertThatThrownBy(() -> service.update(10L, changed)).isInstanceOf(ResponseStatusException.class);
        verify(lessonRepository, never()).saveAll(any());
        verify(classRepository, never()).saveAndFlush(any());
    }

    @Test
    void changingOnlyStatusDoesNotOverwriteIndividualLessonAssignments() {
        var existing = existingClass();
        existing.setTeacher(teacher(30L));
        when(classRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(existing));
        when(lessonRepository.existsByClassEntityId(10L)).thenReturn(true);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(branchRepository.findById(2L)).thenReturn(Optional.of(branch));
        when(classRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        service.update(10L, new ClassUpsertRequest(1L, 2L, "PIA-G1-02", "Tên mới", 8, "IN_PROGRESS",
                request.startDate(), request.endDate()));
        verify(lessonRepository, never()).saveAll(any());
    }

    @Test
    void optionsContainOnlyActiveTeachersOfTheSelectedSubjectAndSuitableRooms() {
        User piano = teacher(30L);
        User guitar = User.builder().id(31L).role(Role.TEACHER).subject("GUITAR").build();
        when(userRepository.findByRole(Role.TEACHER)).thenReturn(List.of(piano, guitar));
        when(roomRepository.findByBranchId(2L)).thenReturn(List.of(room(),
                Room.builder().id(21L).branch(branch).capacity(10).roomType(RoomType.GUITAR_ROOM).build()));
        assertThat(service.getTeacherOptions(1L)).extracting("id").containsExactly(30L);
        assertThat(service.getRoomOptions(1L, 2L, 8)).extracting("id").containsExactly(20L);
        assertThat(service.getRoomOptions(1L, 2L, 11)).isEmpty();
    }

    @Test
    void reassigningTeacherChecksAllAffectedLessonsBeforeSaving() {
        ClassEntity existing = existingClass();
        User newTeacher = teacher(31L);
        LocalDate day = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(10);
        Lesson target = Lesson.builder().id(1L).classEntity(existing).teacher(existing.getTeacher()).room(room())
                .lessonDate(day).startTime(LocalTime.of(17, 0)).endTime(LocalTime.of(19, 0))
                .status(LessonStatus.SCHEDULED).build();
        Lesson conflict = Lesson.builder().id(2L).teacher(newTeacher).lessonDate(day)
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(20, 0)).status(LessonStatus.SCHEDULED).build();
        when(classRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(existing));
        when(lessonRepository.findByClassEntityId(10L)).thenReturn(List.of(target));
        when(userRepository.findByIdForScheduleUpdate(31L)).thenReturn(Optional.of(newTeacher));
        when(lessonRepository.findByTeacherIdAndLessonDateBetween(eq(31L), any(), any())).thenReturn(List.of(conflict));
        assertThatThrownBy(() -> service.update(10L, new ClassUpsertRequest(1L, 2L, 31L, 20L,
                "PIA-G1-02", "Piano", 8, "OPEN", request.startDate(), request.endDate(), null, null, null, null, null)))
                .hasMessageContaining("30 phút");
        verify(lessonRepository, never()).saveAll(any());
        verify(classRepository, never()).saveAndFlush(any());
    }

    @Test
    void canClearUnavailableAssignmentsAndReturnToPlanningWithoutChangingPastLessons() {
        ClassEntity existing = existingClass();
        existing.getTeacher().setStatus(com.talent.management.shared.enums.UserStatus.INACTIVE);
        existing.getRoom().setStatus(com.talent.management.shared.enums.RoomStatus.MAINTENANCE);
        LocalDate futureDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(10);
        Lesson future = Lesson.builder().id(1L).classEntity(existing).teacher(existing.getTeacher()).room(existing.getRoom())
                .lessonDate(futureDate).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0))
                .status(LessonStatus.SCHEDULED).build();
        when(classRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(existing));
        when(lessonRepository.findByClassEntityId(10L)).thenReturn(List.of(future));
        when(classRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        service.update(10L, new ClassUpsertRequest(1L, 2L, null, null, "PIA-G1-02", "Piano", 8, "PLANNING",
                request.startDate(), request.endDate(), null, null, null, null, null, true, true));
        assertThat(existing.getTeacher()).isNull();
        assertThat(existing.getRoom()).isNull();
        assertThat(future.getTeacher()).isNull();
        assertThat(future.getRoom()).isNull();
        assertThat(existing.getStatus()).isEqualTo(ClassStatus.PLANNING);
    }

    private Lesson futureOverride(ClassEntity owner, Room overrideRoom) {
        return Lesson.builder().id(80L).classEntity(owner).teacher(owner.getTeacher()).room(overrideRoom)
                .lessonDate(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(10))
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0)).status(LessonStatus.SCHEDULED).build();
    }

    private void existingWithOverride(ClassEntity owner, Lesson lesson, Room overrideRoom) {
        when(classRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(owner));
        when(lessonRepository.findByClassEntityId(10L)).thenReturn(List.of(lesson));
        when(roomRepository.findByIdForScheduleUpdate(overrideRoom.getId())).thenReturn(Optional.of(overrideRoom));
    }

    @Test
    void increasingClassSizeChecksCapacityOfAnIndividualLessonRoom() {
        ClassEntity existing = existingClass();
        existing.setMaxStudents(4);
        Room smallRoom = Room.builder().id(21L).branch(branch).capacity(4).roomType(RoomType.PIANO_GROUP).build();
        existingWithOverride(existing, futureOverride(existing, smallRoom), smallRoom);
        assertThatThrownBy(() -> service.update(10L, new ClassUpsertRequest(1L, 2L, "PIA-G1-02", "Piano", 8,
                "OPEN", request.startDate(), request.endDate()))).hasMessageContaining("sức chứa");
        verify(classRepository, never()).saveAndFlush(any());
    }

    @Test
    void increasingClassSizeChecksEquipmentOfAnIndividualLessonRoom() {
        ClassEntity existing = existingClass();
        existing.setMaxStudents(1);
        Room override = Room.builder().id(21L).branch(branch).capacity(10).roomType(RoomType.PIANO_GROUP).build();
        existingWithOverride(existing, futureOverride(existing, override), override);
        when(equipmentRepository.findByRoomIdOrderByIdAsc(21L)).thenReturn(List.of(
                Equipment.builder().category(EquipmentCategory.KEYBOARD).build()));
        assertThatThrownBy(() -> service.update(10L, new ClassUpsertRequest(1L, 2L, "PIA-G1-02", "Piano", 2,
                "OPEN", request.startDate(), request.endDate()))).hasMessageContaining("hiện có 1");
        verify(classRepository, never()).saveAndFlush(any());
    }

    @Test
    void changingTeacherPreservesTheRoomChosenForAnIndividualLesson() {
        ClassEntity existing = existingClass();
        Room override = Room.builder().id(21L).branch(branch).capacity(10).roomType(RoomType.GUITAR_ROOM).build();
        // Use a room capable of the existing Piano course, equipped for all eight seats.
        override.setRoomType(RoomType.PIANO_GROUP);
        Lesson lesson = futureOverride(existing, override);
        existingWithOverride(existing, lesson, override);
        when(equipmentRepository.findByRoomIdOrderByIdAsc(21L)).thenReturn(java.util.stream.IntStream.range(0, 8)
                .mapToObj(i -> Equipment.builder().category(EquipmentCategory.KEYBOARD).build()).toList());
        User replacement = teacher(31L);
        when(userRepository.findByIdForScheduleUpdate(31L)).thenReturn(Optional.of(replacement));
        when(classRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        service.update(10L, new ClassUpsertRequest(1L, 2L, 31L, null, "PIA-G1-02", "Piano", 8,
                "OPEN", request.startDate(), request.endDate(), null, null, null, null, null));
        assertThat(lesson.getTeacher()).isSameAs(replacement);
        assertThat(lesson.getRoom()).isSameAs(override);
    }

    @Test
    void startingAClassChecksActualRemainingRoomsRatherThanAnUnusedDefaultRoom() {
        ClassEntity existing = existingClass();
        existing.getRoom().setStatus(com.talent.management.shared.enums.RoomStatus.MAINTENANCE);
        Room override = Room.builder().id(21L).branch(branch).capacity(10).roomType(RoomType.PIANO_GROUP).build();
        existingWithOverride(existing, futureOverride(existing, override), override);
        when(equipmentRepository.findByRoomIdOrderByIdAsc(21L)).thenReturn(java.util.stream.IntStream.range(0, 8)
                .mapToObj(i -> Equipment.builder().category(EquipmentCategory.KEYBOARD).build()).toList());
        when(classRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        var result = service.update(10L, new ClassUpsertRequest(1L, 2L, "PIA-G1-02", "Piano", 8,
                "IN_PROGRESS", request.startDate(), request.endDate()));
        assertThat(result.status()).isEqualTo(ClassStatus.IN_PROGRESS);
        verify(lessonRepository, never()).saveAll(any());
    }
}
