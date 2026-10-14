package com.talent.management.features.attendance_makeup.service;

import com.talent.management.features.attendance_makeup.dto.request.*;
import com.talent.management.features.attendance_makeup.exception.AttendanceMakeupException;
import com.talent.management.features.attendance_makeup.repository.*;
import com.talent.management.features.attendance_makeup.service.impl.ScheduleWeeklyServiceImpl;
import com.talent.management.features.auth.repository.*;
import com.talent.management.features.branch_facility_enrollment.repository.*;
import com.talent.management.features.course_enrollment.repository.CourseRepository;
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
    @InjectMocks ScheduleWeeklyServiceImpl service;

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    private final User teacher = User.builder().id(10L).role(Role.TEACHER).username("teacher").build();
    private final User parent = User.builder().id(20L).role(Role.PARENT).build();
    private final Student child = Student.builder().id(1L).fullName("Bé A").parent(parent).build();
    private final Student sibling = Student.builder().id(2L).fullName("Bé B").parent(parent).build();
    private Lesson lesson(long id) {
        return Lesson.builder().id(id).teacher(teacher).lessonDate(today).sessionNumber(1)
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 0)).status(LessonStatus.SCHEDULED)
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

    @Test void parentCannotReadWholeClassAttendanceSheet() {
        when(lessons.findById(100L)).thenReturn(Optional.of(lesson(100)));
        when(currentUser.getCurrentUser()).thenReturn(parent);
        assertThatThrownBy(() -> service.getLessonAttendanceSheet(100L)).isInstanceOf(AttendanceMakeupException.class);
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
            assertThatThrownBy(() -> service.saveLessonAttendanceBatch(100L, batch(1L, "PRESENT")))
                    .isInstanceOf(AttendanceMakeupException.class);
        }
        verify(records, never()).save(any());
    }

    @Test void teacherCannotMarkSomeoneElsesLesson() {
        Lesson lesson = lesson(100);
        lesson.setTeacher(User.builder().id(99L).build());
        when(lessons.findById(100L)).thenReturn(Optional.of(lesson));
        when(currentUser.getCurrentUser()).thenReturn(teacher);
        assertThatThrownBy(() -> service.saveLessonAttendanceBatch(100L, batch(1L, "PRESENT")))
                .isInstanceOf(AttendanceMakeupException.class);
        verify(records, never()).save(any());
    }

    @Test void rejectsOutsiderIncompleteRosterDuplicateAndInvalidStatusBeforeSaving() {
        roster(lesson(100), child, sibling);
        for (var request : List.of(batch(999L, "PRESENT"), batch(1L, "PRESENT"), batch(1L, "TYPO"),
                BatchAttendanceSaveRequest.builder().attendances(List.of(
                        StudentAttendanceMarkItem.builder().studentId(1L).status("PRESENT").build(),
                        StudentAttendanceMarkItem.builder().studentId(1L).status("ABSENT").build())).build())) {
            assertThatThrownBy(() -> service.saveLessonAttendanceBatch(100L, request))
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
        var sheet = service.getLessonAttendanceSheet(100L);
        assertThat(sheet.isAlreadySaved()).isFalse();
        assertThat(sheet.getStudents()).extracting("status").containsExactly("PRESENT", null);
    }

    @Test void makeupAttendanceCompletesRegistrationAndKeepsLearnerInRoster() {
        Lesson target = lesson(200);
        roster(target);
        MakeupRegistration makeup = MakeupRegistration.builder().id(5L).student(child).originalLesson(lesson(100))
                .targetLesson(target).status(MakeupStatus.SCHEDULED).build();
        when(makeups.findByTargetLessonIdAndStatusIn(eq(200L), any())).thenReturn(List.of(makeup));
        when(makeups.findFirstByTargetLessonIdAndStudentIdAndStatus(200L, 1L, MakeupStatus.SCHEDULED))
                .thenReturn(Optional.of(makeup));
        when(students.findById(1L)).thenReturn(Optional.of(child));
        service.saveLessonAttendanceBatch(200L, batch(1L, "PRESENT"));
        assertThat(makeup.getStatus()).isEqualTo(MakeupStatus.COMPLETED);
        verify(records).save(argThat(a -> a.getStudent().getId() == 1L && a.getStatus() == AttendanceStatus.PRESENT));
        assertThat(service.getLessonAttendanceSheet(200L).getStudents()).extracting("studentId").containsExactly(1L);
    }

    @Test void generatesAllSixtyWeeklyLessonsInsteadOfStoppingAfterOneYear() {
        when(currentUser.getCurrentUser()).thenReturn(User.builder().id(30L).role(Role.STAFF).build());
        when(courses.findById(1L)).thenReturn(Optional.of(Course.builder().id(1L).build()));
        when(branches.findById(2L)).thenReturn(Optional.of(Branch.builder().id(2L).build()));
        when(classes.save(any())).thenAnswer(inv -> { ClassEntity c = inv.getArgument(0); c.setId(3L); return c; });
        var request = CreateClassWithLessonsRequest.builder().courseId(1L).branchId(2L).classCode("C60")
                .className("60 buổi").maxStudents(10).startDate(today).totalSessions(60)
                .daysOfWeek(List.of(today.getDayOfWeek().getValue())).startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(19, 0)).build();
        service.createClassWithLessons(request);
        verify(lessons).saveAll(argThat(items -> {
            List<Lesson> generated = new ArrayList<>(); items.forEach(generated::add);
            return generated.size() == 60 && generated.get(59).getLessonDate().equals(today.plusWeeks(59));
        }));
    }
}
