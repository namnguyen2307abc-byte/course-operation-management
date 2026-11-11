package com.talent.management.features.attendance_makeup.service;

import com.talent.management.features.weekly_schedule.dto.request.*;
import com.talent.management.features.attendance_tracking.dto.request.*;
import com.talent.management.features.attendance_tracking.dto.response.*;
import com.talent.management.features.weekly_schedule.dto.response.*;
import com.talent.management.features.attendance_makeup.exception.AttendanceMakeupException;
import com.talent.management.features.weekly_schedule.repository.*;
import com.talent.management.features.attendance_makeup.repository.*;
import com.talent.management.features.weekly_schedule.service.impl.ScheduleWeeklyServiceImpl;
import com.talent.management.features.auth.repository.*;
import com.talent.management.features.branch_facility_enrollment.repository.*;
import com.talent.management.features.course_enrollment.repository.CourseRepository;
import com.talent.management.features.class_management.service.ClassSchedulingService;
import com.talent.management.features.class_management.repository.ManagedClassLessonRepository;
import com.talent.management.features.branch_facility_enrollment.service.FacilityRulesService;
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
class ScheduleWeeklyFlowTest {
    @Mock AttendanceLessonRepository lessons;
    @Mock AttendanceRecordRepository records;
    @Mock AttendanceEnrollmentRepository enrollments;
    @Mock AttendanceClassRepository classes;
    @Mock AbsenceRequestRepository absences;
    @Mock MakeupRegistrationRepository makeups;
    @Mock StudentRepository students;
    @Mock UserRepository users;
    @Mock CourseRepository courses;
    @Mock BranchRepository branches;
    @Mock RoomRepository rooms;
    @Mock CurrentUserService currentUser;
    @Mock ClassSchedulingService schedulingService;
    @Mock com.talent.management.features.notification.service.NotificationService notifications;
    @InjectMocks ScheduleWeeklyServiceImpl service;
    @InjectMocks com.talent.management.features.attendance_tracking.service.impl.AttendanceTrackingServiceImpl attendanceService;

    @org.junit.jupiter.api.BeforeEach
    void injectNotifications() {
        org.springframework.test.util.ReflectionTestUtils.setField(attendanceService, "notificationService", notifications);
    }

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    private final User teacher = User.builder().id(10L).role(Role.TEACHER).username("teacher").build();
    private final User parent = User.builder().id(20L).role(Role.PARENT).build();
    private final Student child = Student.builder().id(1L).fullName("Bé A").parent(parent).build();
    private final Student sibling = Student.builder().id(2L).fullName("Bé B").parent(parent).build();
    private Lesson lesson(long id) {
        return Lesson.builder().id(id).teacher(teacher).lessonDate(today).sessionNumber(1)
                .startTime(LocalTime.of(0, 1)).endTime(LocalTime.of(23, 59)).status(LessonStatus.SCHEDULED)
                .classEntity(ClassEntity.builder().id(id).className("Lớp " + id).currentStudents(2).build()).build();
    }
    private void parentSchedule(Student... children) {
        when(currentUser.getCurrentUser()).thenReturn(parent);
        when(students.findByParentId(parent.getId())).thenReturn(List.of(children));
        for (Student student : children) when(students.findById(student.getId())).thenReturn(Optional.of(student));
    }
    private void roster(Lesson lesson, Student... roster) {
        when(currentUser.getCurrentUser()).thenReturn(teacher);
        when(lessons.findById(lesson.getId())).thenReturn(Optional.of(lesson));
        when(enrollments.findActiveEnrollmentsByClassId(lesson.getClassEntity().getId()))
                .thenReturn(Arrays.stream(roster).map(s -> Enrollment.builder().student(s).build()).toList());
    }
    private BatchAttendanceSaveRequest batch(Long sid, String status) {
        return BatchAttendanceSaveRequest.builder().attendances(List.of(
                StudentAttendanceMarkItem.builder().studentId(sid).status(status).build())).build();
    }

    @Test void parentSeesOwnChildStatusNotFirstStudentInClass() {
        Lesson lesson = lesson(100);
        parentSchedule(child);
        when(lessons.findLessonsByStudentIdAndDateBetween(eq(1L), any(), any())).thenReturn(List.of(lesson));
        when(records.findByLessonIdIn(anyList())).thenReturn(List.of(
                Attendance.builder().lesson(lesson).student(sibling).status(AttendanceStatus.PRESENT).build(),
                Attendance.builder().lesson(lesson).student(child).status(AttendanceStatus.ABSENT).build()));
        var item = service.getWeeklySchedule(today, null, null, null).getLessons().get(0);
        assertThat(item.getAttendanceStatus()).isEqualTo("ABSENT");
        assertThat(item.getStudentAttendances()).extracting("studentId").containsExactly(1L);
    }

    @Test void unmarkedChildDoesNotInheritAnotherStudentsStatus() {
        Lesson lesson = lesson(100);
        parentSchedule(child);
        when(lessons.findLessonsByStudentIdAndDateBetween(eq(1L), any(), any())).thenReturn(List.of(lesson));
        when(records.findByLessonIdIn(anyList())).thenReturn(List.of(
                Attendance.builder().lesson(lesson).student(sibling).status(AttendanceStatus.PRESENT).build()));
        assertThat(service.getWeeklySchedule(today, null, null, null).getLessons().get(0).getAttendanceStatus())
                .isEqualTo("NOT_MARKED");
    }

    @Test void movingOneSiblingPreservesOtherSiblingOriginalLesson() {
        Lesson original = lesson(100), target = lesson(200);
        parentSchedule(child, sibling);
        when(lessons.findLessonsByStudentIdAndDateBetween(anyLong(), any(), any())).thenReturn(List.of(original));
        when(makeups.findByStudentIdAndStatusIn(eq(1L), any())).thenReturn(List.of(
                MakeupRegistration.builder().student(child).originalLesson(original).targetLesson(target)
                        .status(MakeupStatus.SCHEDULED).build()));
        var result = service.getWeeklySchedule(today, null, null, null).getLessons();
        assertThat(result).extracting("lessonId").containsExactlyInAnyOrder(100L, 200L);
        assertThat(result.stream().filter(l -> l.getLessonId() == 100L).findFirst().orElseThrow().getStudentAttendances())
                .extracting("studentId").containsExactly(2L);
        assertThat(result.stream().filter(l -> l.getLessonId() == 200L).findFirst().orElseThrow().getStudentAttendances())
                .extracting("studentId").containsExactly(1L);
    }

    @Test void teacherCannotMarkBeforeStartAndCancelledLessonCannotBeMarkedByAdmin() {
        Lesson futureToday = lesson(100);
        futureToday.setStartTime(LocalTime.MAX);
        when(currentUser.getCurrentUser()).thenReturn(teacher);
        when(lessons.findById(100L)).thenReturn(Optional.of(futureToday));
        assertThat(attendanceService.getLessonAttendanceSheet(100L).isCanEdit()).isFalse();
        assertThatThrownBy(() -> attendanceService.saveLessonAttendanceBatch(100L, batch(1L, "PRESENT")))
                .isInstanceOf(AttendanceMakeupException.class).hasMessageContaining("Chưa vào ca học");
        futureToday.setStatus(LessonStatus.CANCELLED);
        when(currentUser.getCurrentUser()).thenReturn(User.builder().id(90L).role(Role.ADMIN).build());
        assertThatThrownBy(() -> attendanceService.saveLessonAttendanceBatch(100L, batch(1L, "PRESENT")))
                .isInstanceOf(AttendanceMakeupException.class).hasMessageContaining("hủy");
        verify(records, never()).save(any());
    }

    @Test void firstAttendanceNotifiesParentButUnchangedResaveDoesNot() {
        Lesson target = lesson(100);
        roster(target, child);
        when(students.findById(1L)).thenReturn(Optional.of(child));
        when(records.findByLessonIdAndStudentId(100L, 1L)).thenReturn(Optional.empty(),
                Optional.of(Attendance.builder().lesson(target).student(child).status(AttendanceStatus.PRESENT).build()));
        attendanceService.saveLessonAttendanceBatch(100L, batch(1L, "PRESENT"));
        attendanceService.saveLessonAttendanceBatch(100L, batch(1L, "PRESENT"));
        verify(notifications, times(1)).sendNotification(eq(parent), eq(teacher), anyString(), anyString(),
                eq("ATTENDANCE_PRESENT"), eq(100L), anyString());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"2,NORMAL", "3,WARNING", "4,CRITICAL"})
    void absenceWarningsRespectTenAndTwentyPercentBoundaries(int absenceCount, String expected) {
        when(currentUser.getCurrentUser()).thenReturn(parent);
        when(students.findById(1L)).thenReturn(Optional.of(child));
        ClassEntity c = ClassEntity.builder().id(50L).className("Piano")
                .course(Course.builder().totalSessions(20).build()).build();
        when(classes.findById(50L)).thenReturn(Optional.of(c));
        when(enrollments.findEnrollmentsByStudentIdAndStatusIn(eq(1L), any()))
                .thenReturn(List.of(Enrollment.builder().student(child).classEntity(c).build()));
        List<Lesson> sessions = new ArrayList<>();
        List<Attendance> marks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Lesson l = lesson(100 + i); l.setSessionNumber(i + 1); l.setClassEntity(c);
            sessions.add(l);
            if (i < absenceCount) marks.add(Attendance.builder().lesson(l).student(child).status(AttendanceStatus.ABSENT).build());
        }
        when(lessons.findByClassEntityIdOrderBySessionNumberAscLessonDateAscStartTimeAsc(50L)).thenReturn(sessions);
        when(records.findByLessonIdIn(anyList())).thenReturn(marks);
        assertThat(attendanceService.getStudentAttendanceTracking(1L, 50L).getWarningLevel()).isEqualTo(expected);
    }

    @Test void parentCannotReadWholeClassAttendanceSheet() {
        when(lessons.findById(100L)).thenReturn(Optional.of(lesson(100)));
        when(currentUser.getCurrentUser()).thenReturn(parent);
        assertThatThrownBy(() -> attendanceService.getLessonAttendanceSheet(100L)).isInstanceOf(AttendanceMakeupException.class);
    }

    @Test void unlinkedStudentCannotRequestArbitraryStudentId() {
        when(currentUser.getCurrentUser()).thenReturn(User.builder().id(50L).role(Role.STUDENT).build());
        assertThat(service.getWeeklySchedule(today, 999L, null, null).getLessons()).isEmpty();
        verify(lessons, never()).findLessonsByStudentIdAndDateBetween(anyLong(), any(), any());
    }

    @Test void teacherCannotSavePastOrFutureLessons() {
        when(currentUser.getCurrentUser()).thenReturn(teacher);
        for (int offset : List.of(-1, 1)) {
            Lesson lesson = lesson(100); lesson.setLessonDate(today.plusDays(offset));
            when(lessons.findById(100L)).thenReturn(Optional.of(lesson));
            assertThatThrownBy(() -> attendanceService.saveLessonAttendanceBatch(100L, batch(1L, "PRESENT")))
                    .isInstanceOf(AttendanceMakeupException.class);
        }
        verify(records, never()).save(any());
    }

    @Test void teacherCannotMarkSomeoneElsesLesson() {
        Lesson lesson = lesson(100);
        lesson.setTeacher(User.builder().id(99L).build());
        when(lessons.findById(100L)).thenReturn(Optional.of(lesson));
        when(currentUser.getCurrentUser()).thenReturn(teacher);
        assertThatThrownBy(() -> attendanceService.saveLessonAttendanceBatch(100L, batch(1L, "PRESENT")))
                .isInstanceOf(AttendanceMakeupException.class);
        verify(records, never()).save(any());
    }

    @Test void rejectsOutsiderIncompleteRosterDuplicateAndInvalidStatusBeforeSaving() {
        roster(lesson(100), child, sibling);
        for (var request : List.of(batch(999L, "PRESENT"), batch(1L, "PRESENT"), batch(1L, "TYPO"), batch(1L, "LATE"),
                BatchAttendanceSaveRequest.builder().attendances(List.of(
                        StudentAttendanceMarkItem.builder().studentId(1L).status("PRESENT").build(),
                        StudentAttendanceMarkItem.builder().studentId(1L).status("ABSENT").build())).build())) {
            assertThatThrownBy(() -> attendanceService.saveLessonAttendanceBatch(100L, request))
                    .isInstanceOf(AttendanceMakeupException.class);
        }
        verify(records, never()).save(any());
    }

    @Test void addingMakeupStudentPreservesSavedMarksButRequiresCompletion() {
        Lesson target = lesson(100);
        roster(target, child);
        when(records.findByLessonId(100L)).thenReturn(List.of(
                Attendance.builder().student(child).status(AttendanceStatus.PRESENT).build()));
        when(makeups.findByTargetLessonIdAndStatusIn(eq(100L), any())).thenReturn(List.of(
                MakeupRegistration.builder().student(sibling).targetLesson(target).status(MakeupStatus.SCHEDULED).build()));
        var sheet = attendanceService.getLessonAttendanceSheet(100L);
        assertThat(sheet.isAlreadySaved()).isFalse();
        assertThat(sheet.getStudents()).extracting("status").containsExactly("PRESENT", null);
    }

    @Test void makeupAttendanceCompletesRegistrationAndKeepsLearnerInRoster() {
        Lesson target = lesson(200);
        roster(target);
        MakeupRegistration makeup = MakeupRegistration.builder().id(5L).student(child).originalLesson(lesson(100))
                .targetLesson(target).status(MakeupStatus.SCHEDULED).build();
        when(makeups.findByTargetLessonIdAndStatusIn(eq(200L), any())).thenReturn(List.of(makeup));
        when(makeups.findFirstByTargetLessonIdAndStudentIdAndStatusIn(eq(200L), eq(1L), any()))
                .thenReturn(Optional.of(makeup));
        when(students.findById(1L)).thenReturn(Optional.of(child));
        attendanceService.saveLessonAttendanceBatch(200L, batch(1L, "PRESENT"));
        assertThat(makeup.getStatus()).isEqualTo(MakeupStatus.COMPLETED);
        verify(records).save(argThat(a -> a.getStudent().getId() == 1L && a.getStatus() == AttendanceStatus.PRESENT));
        assertThat(attendanceService.getLessonAttendanceSheet(200L).getStudents()).extracting("studentId").containsExactly(1L);
    }

    @Test void generatesAllSixtyWeeklyLessonsInsteadOfStoppingAfterOneYear() {
        when(currentUser.getCurrentUser()).thenReturn(User.builder().id(30L).role(Role.STAFF).build());
        when(courses.findById(1L)).thenReturn(Optional.of(Course.builder().id(1L).build()));
        when(branches.findById(2L)).thenReturn(Optional.of(Branch.builder().id(2L).build()));
        when(classes.save(any())).thenAnswer(inv -> { ClassEntity c = inv.getArgument(0); c.setId(3L); return c; });
        var request = CreateClassWithLessonsRequest.builder().courseId(1L).branchId(2L).classCode("C60")
                .className("60 buổi").maxStudents(10).startDate(today.plusWeeks(1)).totalSessions(60)
                .daysOfWeek(List.of(today.getDayOfWeek().getValue())).startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(19, 0)).build();
        ClassSchedulingService rules = new ClassSchedulingService(users, rooms, mock(ManagedClassLessonRepository.class),
                new FacilityRulesService(mock(EquipmentRepository.class)));
        when(schedulingService.generateLessons(any(), anyList(), any(), any(), any(), isNull()))
                .thenAnswer(inv -> rules.generateLessons(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2),
                        inv.getArgument(3), inv.getArgument(4), null));
        service.createClassWithLessons(request);
        verify(lessons).saveAll(argThat(items -> {
            List<Lesson> generated = new ArrayList<>(); items.forEach(generated::add);
            return generated.size() == 60 && generated.get(59).getLessonDate().equals(today.plusWeeks(60));
        }));
        verify(schedulingService).validateAssignments(any(), any(), isNull(), isNull(), eq(10));
        verify(schedulingService).validateReadiness(eq(ClassStatus.OPEN), eq(true), isNull(), isNull());
        verify(schedulingService).validateLessonConflicts(argThat(items -> items.size() == 60));
    }
    @Test void parentCanGetAttendanceTrackingForChildWithSummaryAlert() {
        when(currentUser.getCurrentUser()).thenReturn(parent);
        when(students.findById(1L)).thenReturn(Optional.of(child));
        ClassEntity c = ClassEntity.builder().id(50L).classCode("SE2055-JV").className("Lớp Piano")
                .course(Course.builder().id(1L).totalSessions(20).name("Piano").build()).build();
        when(classes.findById(50L)).thenReturn(Optional.of(c));
        when(enrollments.findEnrollmentsByStudentIdAndStatusIn(eq(1L), any()))
                .thenReturn(List.of(Enrollment.builder().student(child).classEntity(c).status(EnrollmentStatus.ENROLLED).build()));
        Lesson l1 = Lesson.builder().id(101L).sessionNumber(1).lessonDate(today).startTime(LocalTime.of(7, 30))
                .endTime(LocalTime.of(9, 50)).classEntity(c).build();
        Lesson l2 = Lesson.builder().id(102L).sessionNumber(2).lessonDate(today.plusDays(3)).startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(12, 20)).classEntity(c).build();
        when(lessons.findByClassEntityIdOrderBySessionNumberAscLessonDateAscStartTimeAsc(50L)).thenReturn(List.of(l1, l2));
        when(records.findByLessonIdIn(List.of(101L, 102L))).thenReturn(List.of(
                Attendance.builder().lesson(l1).student(child).status(AttendanceStatus.ABSENT).build(),
                Attendance.builder().lesson(l2).student(child).status(AttendanceStatus.PRESENT).build()
        ));
        when(makeups.findByStudentIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        var tracking = attendanceService.getStudentAttendanceTracking(1L, 50L);
        assertThat(tracking.getStudentName()).isEqualTo(child.getFullName());
        assertThat(tracking.getTotalSessions()).isEqualTo(20);
        assertThat(tracking.getAttendedCount()).isEqualTo(1);
        assertThat(tracking.getAbsentCount()).isEqualTo(1);
        assertThat(tracking.getUnmarkedCount()).isEqualTo(18);
        assertThat(tracking.getLessons()).hasSize(20);
        assertThat(tracking.getLessons().get(0).getStatus()).isEqualTo("ABSENT");
        assertThat(tracking.getLessons().get(0).getStatusLabel()).isEqualTo("Vắng mặt");
        assertThat(tracking.getLessons().get(1).getStatus()).isEqualTo("PRESENT");
        assertThat(tracking.getLessons().get(1).getStatusLabel()).isEqualTo("Có mặt");
        assertThat(tracking.getLessons().get(2).getStatus()).isEqualTo("NOT_MARKED");
        assertThat(tracking.getLessons().get(2).getStatusLabel()).isEqualTo("Chưa điểm danh");
        assertThat(tracking.getLessons().get(19).getSessionNumber()).isEqualTo(20);
        assertThat(tracking.getSummaryAlert()).contains("Vắng");
    }

    @Test void parentCannotAccessOtherParentChildAttendanceTracking() {
        when(currentUser.getCurrentUser()).thenReturn(parent);
        User otherParent = User.builder().id(99L).role(Role.PARENT).build();
        Student otherChild = Student.builder().id(9L).fullName("Bé Khác").parent(otherParent).build();
        when(students.findById(9L)).thenReturn(Optional.of(otherChild));

        assertThatThrownBy(() -> attendanceService.getStudentAttendanceTracking(9L, 50L))
                .isInstanceOf(AttendanceMakeupException.class)
                .hasMessageContaining("Bạn chỉ có quyền xem thông tin con của mình");
    }
}
