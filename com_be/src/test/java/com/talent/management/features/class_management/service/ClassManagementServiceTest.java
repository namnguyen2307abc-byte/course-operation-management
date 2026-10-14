package com.talent.management.features.class_management.service;

import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.branch_facility_enrollment.repository.BranchRepository;
import com.talent.management.features.branch_facility_enrollment.repository.RoomRepository;
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
    @InjectMocks ClassManagementService service;

    private Course course;
    private Branch branch;
    private ClassUpsertRequest request;


    @BeforeEach
    void setUp() {
        course = Course.builder().id(1L).code("PIA-G1").name("Piano Grade 1").build();
        branch = Branch.builder().id(2L).name("Cơ sở A").build();
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
        when(classRepository.findById(10L)).thenReturn(Optional.of(existing));
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
        when(classRepository.findById(10L)).thenReturn(Optional.of(existingClass()));
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
        Room room = Room.builder().id(20L).build();
        User teacher = User.builder().id(30L).build();
        existing.setRoom(room);
        existing.setTeacher(teacher);
        existing.setScheduleDescription("Thứ 2 hàng tuần");
        when(classRepository.findById(10L)).thenReturn(Optional.of(existing));
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
                .classCode("PIA-G1-02").className("Lớp Piano")
                .maxStudents(8).currentStudents(0).status(ClassStatus.OPEN)
                .startDate(request.startDate()).endDate(request.endDate()).build();
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
        when(userRepository.findById(99L)).thenReturn(Optional.of(User.builder().id(99L).role(Role.PARENT).build()));
        var wrongTeacher = new ClassUpsertRequest(1L, 2L, 99L, null, "C1", "Lớp", 8, "OPEN",
                request.startDate(), request.endDate(), null, null, null, null, null);
        assertThatThrownBy(() -> service.create(wrongTeacher)).isInstanceOf(ResponseStatusException.class);
        when(roomRepository.findById(20L)).thenReturn(Optional.of(Room.builder().id(20L)
                .branch(Branch.builder().id(3L).build()).build()));
        var wrongRoom = new ClassUpsertRequest(1L, 2L, null, 20L, "C1", "Lớp", 8, "OPEN",
                request.startDate(), request.endDate(), null, null, null, null, null);
        assertThatThrownBy(() -> service.create(wrongRoom)).isInstanceOf(ResponseStatusException.class);
        verify(classRepository, never()).saveAndFlush(any());
    }

    @Test
    void editingMetadataPreservesPastAndCompletedLessonAssignments() {
        var existing = existingClass();
        User oldTeacher = User.builder().id(30L).role(Role.TEACHER).build();
        User newTeacher = User.builder().id(31L).role(Role.TEACHER).build();
        existing.setTeacher(oldTeacher);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        Lesson past = Lesson.builder().lessonDate(today.minusDays(1)).teacher(oldTeacher).status(LessonStatus.SCHEDULED).build();
        Lesson completed = Lesson.builder().lessonDate(today).teacher(oldTeacher).status(LessonStatus.COMPLETED).build();
        Lesson upcoming = Lesson.builder().lessonDate(today.plusDays(1)).teacher(oldTeacher).status(LessonStatus.SCHEDULED).build();
        when(classRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(lessonRepository.existsByClassEntityId(10L)).thenReturn(true);
        when(lessonRepository.findByClassEntityId(10L)).thenReturn(List.of(past, completed, upcoming));
        when(userRepository.findById(31L)).thenReturn(Optional.of(newTeacher));
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
        when(classRepository.findById(10L)).thenReturn(Optional.of(existingClass()));
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
        existing.setTeacher(User.builder().id(30L).role(Role.TEACHER).build());
        when(classRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(lessonRepository.existsByClassEntityId(10L)).thenReturn(true);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(branchRepository.findById(2L)).thenReturn(Optional.of(branch));
        when(classRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        service.update(10L, new ClassUpsertRequest(1L, 2L, "PIA-G1-02", "Tên mới", 8, "IN_PROGRESS",
                request.startDate(), request.endDate()));
        verify(lessonRepository, never()).findByClassEntityId(any());
        verify(lessonRepository, never()).saveAll(any());
    }
}
