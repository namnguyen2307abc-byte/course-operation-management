package com.talent.management.features.attendance_makeup.service;

import com.talent.management.features.attendance_makeup.dto.request.*;
import com.talent.management.features.attendance_makeup.exception.AttendanceMakeupException;
import com.talent.management.features.attendance_makeup.mapper.AttendanceMakeupMapper;
import com.talent.management.features.attendance_makeup.repository.*;
import com.talent.management.features.weekly_schedule.repository.*;
import com.talent.management.features.attendance_makeup.service.impl.AbsenceMakeupServiceImpl;
import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.features.notification.service.NotificationService;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import com.talent.management.shared.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
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
    @Mock AttendanceEnrollmentRepository enrollments;
    @Mock StudentRepository students;
    @Mock AttendanceMakeupMapper mapper;
    @Mock CurrentUserService currentUser;
    @Mock NotificationService notificationService;
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
        staff();
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
    @Test void scheduleMakeupRejectsWhenStudentHasOverlappingClassAtSameTime() {
        staff();
        var makeup = request();
        LocalDate date = LocalDate.now().plusDays(2);
        Lesson target = Lesson.builder().id(20L).sessionNumber(3).lessonDate(date)
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 30))
                .status(LessonStatus.SCHEDULED).classEntity(ClassEntity.builder().id(200L)
                        .className("Lớp Bù").course(makeup.getOriginalLesson().getClassEntity().getCourse())
                        .currentStudents(2).maxStudents(8).build()).build();
        Lesson conflict = Lesson.builder().id(99L).lessonDate(date)
                .startTime(LocalTime.of(18, 30)).endTime(LocalTime.of(20, 0))
                .status(LessonStatus.SCHEDULED)
                .classEntity(ClassEntity.builder().id(300L).className("Lớp Trùng").build()).build();
        when(makeups.findById(50L)).thenReturn(Optional.of(makeup));
        when(lessons.findById(20L)).thenReturn(Optional.of(target));
        when(lessons.findLessonsByStudentIdAndDateBetween(1L, date, date)).thenReturn(List.of(conflict));

        assertThatThrownBy(() -> service.scheduleMakeup(50L, new MakeupScheduleRequest(20L, "Xếp bù")))
                .isInstanceOf(AttendanceMakeupException.class)
                .hasMessageContaining("trùng lịch học");
        verify(makeups, never()).save(any());
    }
    @Test void getAbsenceRequestsPrioritizesPendingAndNewestFirst() {
        staff();
        LocalDateTime now = LocalDateTime.now();
        AbsenceRequest oldApproved = AbsenceRequest.builder().id(1L).status(AbsenceStatus.APPROVED).createdAt(now).build();
        AbsenceRequest olderPending = AbsenceRequest.builder().id(2L).status(AbsenceStatus.PENDING).createdAt(now.minusHours(2)).build();
        AbsenceRequest newerPending = AbsenceRequest.builder().id(3L).status(AbsenceStatus.PENDING).createdAt(now.minusMinutes(10)).build();
        when(absences.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(oldApproved, olderPending, newerPending));
        when(makeups.findByAbsenceRequestIdIn(anyList())).thenReturn(List.of());
        when(mapper.toAbsenceResponse(any(), any())).thenAnswer(inv -> {
            AbsenceRequest ar = inv.getArgument(0);
            return com.talent.management.features.attendance_makeup.dto.response.AbsenceRequestResponse.builder()
                    .id(ar.getId()).status(ar.getStatus()).build();
        });

        var result = service.getAbsenceRequests(null, null, null);
        assertThat(result).extracting("id").containsExactly(3L, 2L, 1L);
    }
    @Test void staffApprovingOnBehalfNotifiesStaffAdminAndFallbackClassTeacher() {
        ReflectionTestUtils.setField(service, "notificationService", notificationService);
        staff();
        User classTeacher = User.builder().id(88L).role(Role.TEACHER).fullName("Cô Hoa").build();
        User parent = User.builder().id(77L).role(Role.PARENT).fullName("Phụ huynh A").build();
        Student student = Student.builder().id(1L).fullName("Bé A").parent(parent).build();
        Lesson lesson = Lesson.builder().id(10L).lessonDate(LocalDate.now().plusDays(1))
                .teacher(null).classEntity(ClassEntity.builder().id(100L).className("Piano 1").teacher(classTeacher).build()).build();
        AbsenceRequest ar = AbsenceRequest.builder().id(5L).student(student).lesson(lesson).status(AbsenceStatus.PENDING).build();

        when(absences.findByIdForUpdate(5L)).thenReturn(Optional.of(ar));
        when(absences.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(makeups.findByAbsenceRequestId(5L)).thenReturn(Optional.empty());
        when(makeups.save(any())).thenAnswer(inv -> {
            MakeupRegistration m = inv.getArgument(0);
            m.setId(55L);
            return m;
        });

        service.reviewAbsenceRequest(5L, new AbsenceReviewRequest(AbsenceReviewRequest.ReviewDecision.APPROVED, "Duyệt khẩn cấp"));

        verify(notificationService).sendToRoles(eq(List.of(Role.STAFF, Role.ADMIN, Role.BRANCH_MANAGER)), any(), anyString(), anyString(), eq("MAKEUP_PENDING"), eq(55L), anyString());
        verify(notificationService).sendNotification(eq(parent), any(), anyString(), anyString(), eq("ABSENCE_APPROVED"), eq(5L), anyString());
        verify(notificationService).sendNotification(eq(classTeacher), any(), anyString(), contains("thay mặt bạn"), eq("ABSENCE_APPROVED"), eq(5L), anyString());
    }
    @Test void parentCreateAndCancelPendingAbsenceNotifiesTeacherAndManagers() {
        ReflectionTestUtils.setField(service, "notificationService", notificationService);
        User parent = User.builder().id(10L).role(Role.PARENT).username("parent_lan").build();
        User teacher = User.builder().id(4L).role(Role.TEACHER).fullName("Cô Hường").build();
        Student student = Student.builder().id(1L).fullName("Bé A").parent(parent).build();
        Lesson lesson = Lesson.builder().id(10L).lessonDate(LocalDate.now().plusDays(2))
                .teacher(teacher).classEntity(ClassEntity.builder().id(100L).className("Piano 1").build()).build();

        when(currentUser.getCurrentUser()).thenReturn(parent);
        when(students.findById(1L)).thenReturn(Optional.of(student));
        when(lessons.findById(10L)).thenReturn(Optional.of(lesson));
        when(absences.findByStudentIdAndLessonId(1L, 10L)).thenReturn(Optional.empty());
        when(absences.save(any())).thenAnswer(inv -> {
            AbsenceRequest ar = inv.getArgument(0);
            ar.setId(88L);
            return ar;
        });

        service.createAbsenceRequest(new AbsenceRequestCreateRequest(1L, 10L, "Bé sốt"));
        verify(notificationService).sendNotification(eq(teacher), eq(parent), anyString(), anyString(), eq("ABSENCE_CREATED"), eq(88L), anyString());
        verify(notificationService).sendToRoles(eq(List.of(Role.STAFF, Role.ADMIN, Role.BRANCH_MANAGER)), eq(parent), anyString(), anyString(), eq("ABSENCE_CREATED"), eq(88L), anyString());

        AbsenceRequest pending = AbsenceRequest.builder().id(88L).student(student).lesson(lesson).status(AbsenceStatus.PENDING).build();
        when(absences.findByIdForUpdate(88L)).thenReturn(Optional.of(pending));
        service.cancelAbsenceRequest(88L);
        verify(absences).delete(pending);
        verify(notificationService).sendNotification(eq(teacher), eq(parent), anyString(), anyString(), eq("ABSENCE_CANCELLED"), eq(88L), anyString());
        verify(notificationService).sendToRoles(eq(List.of(Role.STAFF, Role.ADMIN, Role.BRANCH_MANAGER)), eq(parent), anyString(), anyString(), eq("ABSENCE_CANCELLED"), eq(88L), anyString());
    }
    @Test void cancellationRejectsOtherFamiliesAndAlreadyReviewedRequests() {
        User parent = User.builder().id(10L).role(Role.PARENT).build();
        Student student = Student.builder().id(1L).parent(User.builder().id(99L).build()).build();
        AbsenceRequest ar = AbsenceRequest.builder().id(8L).student(student).status(AbsenceStatus.PENDING).build();
        when(currentUser.getCurrentUser()).thenReturn(parent);
        when(absences.findByIdForUpdate(8L)).thenReturn(Optional.of(ar));
        assertThatThrownBy(() -> service.cancelAbsenceRequest(8L)).isInstanceOf(AttendanceMakeupException.class);
        student.setParent(parent);
        ar.setStatus(AbsenceStatus.APPROVED);
        assertThatThrownBy(() -> service.cancelAbsenceRequest(8L)).isInstanceOf(AttendanceMakeupException.class)
                .hasMessageContaining("Chờ duyệt");
        verify(absences, never()).delete(any());
    }

    @Test void teacherCannotCancelAnotherTeachersRequest() {
        when(currentUser.getCurrentUser()).thenReturn(User.builder().id(10L).role(Role.TEACHER).build());
        AbsenceRequest ar = AbsenceRequest.builder().id(8L).student(Student.builder().id(1L).build())
                .lesson(Lesson.builder().teacher(User.builder().id(99L).build()).build()).status(AbsenceStatus.PENDING).build();
        when(absences.findByIdForUpdate(8L)).thenReturn(Optional.of(ar));
        assertThatThrownBy(() -> service.cancelAbsenceRequest(8L)).isInstanceOf(AttendanceMakeupException.class);
        verify(absences, never()).delete(any());
    }

}
