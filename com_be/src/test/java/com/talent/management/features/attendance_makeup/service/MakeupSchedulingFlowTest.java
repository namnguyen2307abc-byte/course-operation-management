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
import org.junit.jupiter.api.BeforeEach;
import com.talent.management.features.attendance_makeup.policy.*;
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
    @Mock AttendanceRecordRepository records;
    @InjectMocks AbsenceMakeupServiceImpl service;

    @BeforeEach void configureExtractedPolicies() {
        var validator = new AbsenceMakeupValidator(lessons, enrollments, makeups);
        var quota = new MakeupQuotaPolicy(makeups);
        var helper = new AttendanceMakeupNotificationHelper(validator, quota);
        ReflectionTestUtils.setField(helper, "notificationService", notificationService);
        ReflectionTestUtils.setField(service, "validator", validator);
        ReflectionTestUtils.setField(service, "quotaPolicy", quota);
        ReflectionTestUtils.setField(service, "notificationHelper", helper);
        lenient().when(enrollments.existsByStudentIdAndClassEntityIdAndStatus(anyLong(), anyLong(), eq(EnrollmentStatus.ENROLLED))).thenReturn(true);
    }

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
    @Test void noSlotMessageCancelsRegistrationWithoutTargetLesson() {
        staff();
        var makeup = request();
        when(makeups.findByIdForUpdate(50L)).thenReturn(Optional.of(makeup));
        when(makeups.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.markNoSlotAvailable(50L, new MakeupResolveRequest("Trung tâm sẽ liên hệ khi có lớp phù hợp."));
        assertThat(makeup.getStatus()).isEqualTo(MakeupStatus.CANCELLED);
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
        when(makeups.findByIdForUpdate(50L)).thenReturn(Optional.of(makeup));
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
        when(makeups.findByIdForUpdate(50L)).thenReturn(Optional.of(makeup));
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
        User parent = User.builder().id(10L).role(Role.PARENT).username("parent_lan").build();
        User teacher = User.builder().id(4L).role(Role.TEACHER).fullName("Cô Hường").build();
        Student student = Student.builder().id(1L).fullName("Bé A").parent(parent).build();
        Lesson lesson = Lesson.builder().id(10L).lessonDate(LocalDate.now().plusDays(2))
                .teacher(teacher).classEntity(ClassEntity.builder().id(100L).className("Piano 1").build()).build();

        when(currentUser.getCurrentUser()).thenReturn(parent);
        when(students.findById(1L)).thenReturn(Optional.of(student));
        when(lessons.findById(10L)).thenReturn(Optional.of(lesson));
        when(absences.existsByStudentIdAndLessonIdAndStatusIn(eq(1L), eq(10L), anyCollection())).thenReturn(false);
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

    @Test void createAbsenceRequest_rejectsWhenCourseQuotaIsExhausted() {
        User parent = User.builder().id(10L).role(Role.PARENT).username("parent1").build();
        Student student = Student.builder().id(1L).fullName("Nguyễn Bảo Nam").parent(parent).build();
        Course course = Course.builder().id(5L).name("Khóa học Piano Grade 1").build();
        ClassEntity clazz = ClassEntity.builder().id(100L).course(course).className("Piano Grade 1").build();
        Lesson futureLesson = Lesson.builder()
                .id(10L)
                .classEntity(clazz)
                .lessonDate(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(14, 0))
                .status(LessonStatus.SCHEDULED)
                .build();

        when(currentUser.getCurrentUser()).thenReturn(parent);
        when(students.findById(1L)).thenReturn(Optional.of(student));
        when(lessons.findById(10L)).thenReturn(Optional.of(futureLesson));
        when(absences.existsByStudentIdAndLessonIdAndStatusIn(eq(1L), eq(10L), anyCollection())).thenReturn(false);
        when(makeups.countQuotaByStudentAndCourse(eq(1L), eq(5L), anyList(), anyString())).thenReturn(3L);

        assertThatThrownBy(() -> service.createAbsenceRequest(new AbsenceRequestCreateRequest(1L, 10L, "Bé có việc gia đình")))
                .isInstanceOf(AttendanceMakeupException.class)
                .hasMessageContaining("3")
                .hasMessageContaining("3");
        verify(absences, never()).save(any());
    }

    @Test void createAbsenceRequest_doesNotCountPendingRequestsAgainstCourseQuota() {
        User parent = User.builder().id(10L).role(Role.PARENT).username("parent1").build();
        User teacher = User.builder().id(4L).role(Role.TEACHER).fullName("Cô Hường").build();
        Student student = Student.builder().id(1L).fullName("Nguyễn Bảo Nam").parent(parent).build();
        Course course = Course.builder().id(5L).name("Khóa học Piano Grade 1").build();
        ClassEntity clazz = ClassEntity.builder().id(100L).course(course).className("Piano Grade 1").teacher(teacher).build();
        Lesson futureLesson = Lesson.builder()
                .id(10L)
                .classEntity(clazz)
                .lessonDate(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(14, 0))
                .status(LessonStatus.SCHEDULED)
                .build();

        when(currentUser.getCurrentUser()).thenReturn(parent);
        when(students.findById(1L)).thenReturn(Optional.of(student));
        when(lessons.findById(10L)).thenReturn(Optional.of(futureLesson));
        when(absences.existsByStudentIdAndLessonIdAndStatusIn(eq(1L), eq(10L), anyCollection())).thenReturn(false);
        when(makeups.countQuotaByStudentAndCourse(eq(1L), eq(5L), eq(MakeupQuotaPolicy.COUNTED_MAKEUP_STATUSES), anyString()))
                .thenReturn(2L);
        when(absences.save(any())).thenAnswer(inv -> {
            AbsenceRequest ar = inv.getArgument(0);
            ar.setId(10014L);
            return ar;
        });
        when(mapper.toAbsenceResponse(any(), isNull())).thenAnswer(inv -> new com.talent.management.features.attendance_makeup.dto.response.AbsenceRequestResponse());

        var resp = service.createAbsenceRequest(new AbsenceRequestCreateRequest(1L, 10L, "Gia đình có việc đột xuất"));

        assertThat(resp.getCourseMakeupCount()).isEqualTo(2);
        verify(students).findByIdForUpdate(1L);
        verify(notificationService).sendNotification(eq(teacher), eq(parent), anyString(), contains("Đã xếp bù khóa: 2/3"), eq("ABSENCE_CREATED"), eq(10014L), anyString());
    }

    @Test void reviewAbsenceRequest_approvalNotificationShowsRealScheduledQuotaOnly() {
        staff();
        User parent = User.builder().id(77L).role(Role.PARENT).fullName("Phụ huynh A").build();
        Student student = Student.builder().id(1L).fullName("Bé A").parent(parent).build();
        Course course = Course.builder().id(5L).name("Piano Grade 1").build();
        Lesson lesson = Lesson.builder().id(10L).lessonDate(LocalDate.now().plusDays(2))
                .classEntity(ClassEntity.builder().id(100L).className("Piano 1").course(course).build()).build();
        AbsenceRequest ar = AbsenceRequest.builder().id(5L).student(student).lesson(lesson).status(AbsenceStatus.PENDING).build();

        when(absences.findByIdForUpdate(5L)).thenReturn(Optional.of(ar));
        when(absences.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(makeups.findByAbsenceRequestId(5L)).thenReturn(Optional.empty());
        when(makeups.save(any())).thenAnswer(inv -> {
            MakeupRegistration m = inv.getArgument(0);
            m.setId(55L);
            return m;
        });
        when(makeups.countQuotaByStudentAndCourse(eq(1L), eq(5L), eq(MakeupQuotaPolicy.COUNTED_MAKEUP_STATUSES), anyString()))
                .thenReturn(2L);

        service.reviewAbsenceRequest(5L, new AbsenceReviewRequest(AbsenceReviewRequest.ReviewDecision.APPROVED, "Duyệt"));

        verify(students).findByIdForUpdate(1L);
        verify(notificationService).sendNotification(
                eq(parent), any(), anyString(), contains("Đã xếp bù khóa: 2/3"), eq("ABSENCE_APPROVED"), eq(5L), anyString());
        verify(notificationService).sendToRoles(
                eq(List.of(Role.STAFF, Role.ADMIN, Role.BRANCH_MANAGER)), any(), anyString(),
                contains("Đã xếp bù khóa: 2/3"), eq("MAKEUP_PENDING"), eq(55L), anyString());
    }

    @Test void scheduleMakeup_rejectsWhenCourseAlreadyHasThreeScheduledOrMissedMakeups() {
        staff();
        var makeup = request();
        when(makeups.findByIdForUpdate(50L)).thenReturn(Optional.of(makeup));
        when(makeups.countQuotaByStudentAndCourse(eq(1L), eq(1L), eq(MakeupQuotaPolicy.COUNTED_MAKEUP_STATUSES), anyString()))
                .thenReturn(3L);

        assertThatThrownBy(() -> service.scheduleMakeup(50L, new MakeupScheduleRequest(20L, "Xếp ca thứ 4")))
                .isInstanceOf(AttendanceMakeupException.class)
                .hasMessageContaining("3/3");
        verify(students).findByIdForUpdate(1L);
        verify(makeups, never()).save(any());
    }

    @Test void getAvailableLessonsForMakeup_returnsOpenFlexibleSlotAfterTenFullCandidates() {
        staff();
        var makeup = request();
        Course course = makeup.getOriginalLesson().getClassEntity().getCourse();
        List<Lesson> flexibleCandidates = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            flexibleCandidates.add(Lesson.builder()
                    .id(200L + i).sessionNumber(4).lessonDate(LocalDate.now().plusDays(1))
                    .startTime(LocalTime.of(14, 0)).endTime(LocalTime.of(16, 0))
                    .status(LessonStatus.SCHEDULED)
                    .classEntity(ClassEntity.builder().id(300L + i).course(course)
                            .currentStudents(8).maxStudents(8).build())
                    .build());
        }
        Lesson openEleventh = Lesson.builder()
                .id(211L).sessionNumber(4).lessonDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(16, 30)).endTime(LocalTime.of(18, 30))
                .status(LessonStatus.SCHEDULED)
                .classEntity(ClassEntity.builder().id(311L).course(course)
                        .currentStudents(2).maxStudents(8).build())
                .build();
        flexibleCandidates.add(openEleventh);

        when(makeups.findById(50L)).thenReturn(Optional.of(makeup));
        when(lessons.findFlexibleMakeupCandidatesByCourse(any(), eq(3), eq(100L), eq(1L))).thenReturn(flexibleCandidates);
        when(mapper.toLessonOption(any(), anyInt(), anyBoolean(), anyString())).thenAnswer(inv -> {
            Lesson l = inv.getArgument(0);
            return com.talent.management.features.attendance_makeup.dto.response.LessonOptionResponse.builder()
                    .id(l.getId()).exactSessionMatch(inv.getArgument(2)).build();
        });

        var options = service.getAvailableLessonsForMakeup(50L);

        assertThat(options).extracting("id").containsExactly(211L);
        assertThat(options).extracting("exactSessionMatch").containsExactly(false);
    }

    @Test void createAbsenceRequest_rejectsWithinTwoHoursBeforeLessonStart() {
        User parent = User.builder().id(10L).role(Role.PARENT).username("parent1").build();
        Student student = Student.builder().id(1L).fullName("Nguyễn Bảo Nam").parent(parent).build();
        LocalDateTime soonStart = LocalDateTime.now(AbsenceMakeupValidator.CENTER_ZONE).plusMinutes(90);
        Lesson soonLesson = Lesson.builder()
                .id(11L)
                .lessonDate(soonStart.toLocalDate())
                .startTime(soonStart.toLocalTime())
                .endTime(soonStart.toLocalTime().plusHours(2))
                .status(LessonStatus.SCHEDULED)
                .classEntity(ClassEntity.builder().id(100L).course(Course.builder().id(5L).build()).build())
                .build();

        when(currentUser.getCurrentUser()).thenReturn(parent);
        when(students.findById(1L)).thenReturn(Optional.of(student));
        when(lessons.findById(11L)).thenReturn(Optional.of(soonLesson));

        assertThatThrownBy(() -> service.createAbsenceRequest(new AbsenceRequestCreateRequest(1L, 11L, "Bé bị ốm")))
                .isInstanceOf(AttendanceMakeupException.class)
                .hasMessageContaining("2 tiếng");
        verify(absences, never()).save(any());
    }

    @Test void getLessonsForAbsenceRequest_excludesLessonsStartingWithinTwoHours() {
        User parent = User.builder().id(10L).role(Role.PARENT).username("parent1").build();
        Student student = Student.builder().id(1L).fullName("Nguyễn Bảo Nam").parent(parent).build();
        ClassEntity clazz = ClassEntity.builder().id(100L).course(Course.builder().id(5L).build()).build();
        LocalDateTime centerNow = LocalDateTime.now(AbsenceMakeupValidator.CENTER_ZONE);
        LocalDateTime soonStart = centerNow.plusMinutes(90);
        LocalDateTime eligibleStart = centerNow.plusHours(3);

        Lesson tooSoon = Lesson.builder().id(11L).lessonDate(soonStart.toLocalDate())
                .startTime(soonStart.toLocalTime()).status(LessonStatus.SCHEDULED).classEntity(clazz).build();
        Lesson eligible = Lesson.builder().id(12L).lessonDate(eligibleStart.toLocalDate())
                .startTime(eligibleStart.toLocalTime()).status(LessonStatus.SCHEDULED).classEntity(clazz).build();

        when(currentUser.getCurrentUser()).thenReturn(parent);
        when(students.findById(1L)).thenReturn(Optional.of(student));
        when(absences.findByStudentIdAndStatusIn(eq(1L), anyList())).thenReturn(List.of());
        when(lessons.findLessonsByStudentId(1L)).thenReturn(List.of(tooSoon, eligible));
        when(mapper.toLessonOption(any())).thenAnswer(inv -> com.talent.management.features.attendance_makeup.dto.response.LessonOptionResponse
                .builder().id(((Lesson) inv.getArgument(0)).getId()).build());

        var result = service.getLessonsForAbsenceRequest(1L);

        assertThat(result).extracting("id").containsExactly(12L);
    }

    @Test void cancelMakeup_rejectsMissedOrAlreadyStartedMakeup() {
        staff();
        var missed = request();
        missed.setStatus(MakeupStatus.MISSED);
        when(makeups.findByIdForUpdate(50L)).thenReturn(Optional.of(missed));
        assertThatThrownBy(() -> service.cancelMakeup(50L, new MakeupCancelRequest("Hủy nhầm")))
                .isInstanceOf(AttendanceMakeupException.class);

        var started = request();
        started.setStatus(MakeupStatus.SCHEDULED);
        started.setTargetLesson(Lesson.builder().id(20L).lessonDate(LocalDate.now().minusDays(1))
                .startTime(LocalTime.of(9, 0)).build());
        when(makeups.findByIdForUpdate(51L)).thenReturn(Optional.of(started));
        assertThatThrownBy(() -> service.cancelMakeup(51L, new MakeupCancelRequest("Hủy sau buổi học")))
                .isInstanceOf(AttendanceMakeupException.class)
                .hasMessageContaining("đã diễn ra");

        verify(makeups, never()).save(any());
    }

    @Test void markNoSlotAvailable_rejectsMissedMakeup() {
        staff();
        var missed = request();
        missed.setStatus(MakeupStatus.MISSED);
        when(makeups.findByIdForUpdate(50L)).thenReturn(Optional.of(missed));

        assertThatThrownBy(() -> service.markNoSlotAvailable(50L, new MakeupResolveRequest("Chưa có lớp")))
                .isInstanceOf(AttendanceMakeupException.class);
        verify(makeups, never()).save(any());
    }

    @Test void createAbsenceRequest_rejectsFinishedClass() {
        User parent = User.builder().id(10L).role(Role.PARENT).username("parent1").build();
        Student student = Student.builder().id(1L).fullName("Nguyễn Bảo Nam").parent(parent).build();
        Lesson lesson = Lesson.builder().id(10L).lessonDate(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(14, 0)).status(LessonStatus.SCHEDULED)
                .classEntity(ClassEntity.builder().id(100L).status(ClassStatus.FINISHED)
                        .course(Course.builder().id(5L).build()).build())
                .build();

        when(currentUser.getCurrentUser()).thenReturn(parent);
        when(students.findById(1L)).thenReturn(Optional.of(student));
        when(lessons.findById(10L)).thenReturn(Optional.of(lesson));

        assertThatThrownBy(() -> service.createAbsenceRequest(new AbsenceRequestCreateRequest(1L, 10L, "Xin nghỉ")))
                .isInstanceOf(AttendanceMakeupException.class)
                .hasMessageContaining("kết thúc");
        verify(absences, never()).save(any());
    }
}
