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
}
