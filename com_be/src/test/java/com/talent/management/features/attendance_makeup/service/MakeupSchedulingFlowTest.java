package com.talent.management.features.attendance_makeup.service;

import com.talent.management.features.attendance_makeup.dto.request.*;
import com.talent.management.features.attendance_makeup.mapper.AttendanceMakeupMapper;
import com.talent.management.features.attendance_makeup.repository.*;
import com.talent.management.features.attendance_makeup.service.impl.AbsenceMakeupServiceImpl;
import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import com.talent.management.shared.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MakeupSchedulingFlowTest {
    @Mock AbsenceRequestRepository absences;
    @Mock MakeupRegistrationRepository makeups;
    @Mock AttendanceLessonRepository lessons;
    @Mock AttendanceRecordRepository records;
    @Mock AttendanceEnrollmentRepository enrollments;
    @Mock StudentRepository students;
    @Mock AttendanceMakeupMapper mapper;
    @Mock CurrentUserService currentUser;
    @InjectMocks AbsenceMakeupServiceImpl service;

    private MakeupRegistration request() {
        Course course = Course.builder().id(1L).level("1").build();
        Lesson original = Lesson.builder().id(10L).sessionNumber(3)
                .classEntity(ClassEntity.builder().id(100L).course(course).build()).build();
        return MakeupRegistration.builder().id(50L).student(Student.builder().id(1L).fullName("Bé A").build())
                .originalLesson(original).status(MakeupStatus.PENDING).build();
    }
    private void staff() {
        when(currentUser.getCurrentUser()).thenReturn(User.builder().id(30L).role(Role.STAFF).username("staff").build());
    }
    @Test void noMatchingClassReturnsEmptyListAndDoesNotPickAnUnrelatedLesson() {
        when(makeups.findById(50L)).thenReturn(Optional.of(request()));
        assertThat(service.getAvailableLessonsForMakeup(50L)).isEmpty();
        verify(lessons).findMakeupCandidatesByCourse(any(), eq(3), eq(100L), eq(1L));
        verify(makeups, never()).save(any());
    }
    @Test void noSlotMessageKeepsRegistrationWithoutTargetLesson() {
        staff();
        var makeup = request();
        when(makeups.findByIdAndStatusIn(eq(50L), anyList())).thenReturn(Optional.of(makeup));
        when(makeups.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.markNoSlotAvailable(50L, new MakeupResolveRequest("Trung tâm sẽ liên hệ khi có lớp phù hợp."));
        assertThat(makeup.getStatus()).isEqualTo(MakeupStatus.NO_SLOT);
        assertThat(makeup.getTargetLesson()).isNull();
        assertThat(makeup.getNote()).contains("Trung tâm sẽ liên hệ");
    }
    @Test void choosingAvailableLessonSchedulesMakeupWithoutChangingOriginalClass() {
        staff();
        var makeup = request();
        Lesson target = Lesson.builder().id(20L).sessionNumber(3).lessonDate(LocalDate.now().plusDays(1))
                .status(LessonStatus.SCHEDULED).classEntity(ClassEntity.builder().id(200L)
                        .course(makeup.getOriginalLesson().getClassEntity().getCourse())
                        .currentStudents(2).maxStudents(8).build()).build();
        when(makeups.findById(50L)).thenReturn(Optional.of(makeup));
        when(lessons.findById(20L)).thenReturn(Optional.of(target));
        when(makeups.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.scheduleMakeup(50L, new MakeupScheduleRequest(20L, "Buổi bù đã xếp"));
        assertThat(makeup.getStatus()).isEqualTo(MakeupStatus.SCHEDULED);
        assertThat(makeup.getTargetLesson()).isSameAs(target);
        assertThat(makeup.getOriginalLesson().getClassEntity().getId()).isEqualTo(100L);
    }
}
