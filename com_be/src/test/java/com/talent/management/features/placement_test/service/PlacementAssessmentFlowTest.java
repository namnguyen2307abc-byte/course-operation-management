package com.talent.management.features.placement_test.service;

import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.course_enrollment.repository.CourseRepository;
import com.talent.management.features.course_enrollment.repository.EnrollmentRequestRepository;
import com.talent.management.features.placement_test.dto.request.PlacementAssessmentRequest;
import com.talent.management.features.placement_test.dto.request.PlacementCriteriaScoreItemRequest;
import com.talent.management.features.placement_test.dto.request.SavePlacementCriteriaRequest;
import com.talent.management.features.placement_test.entity.PlacementCriteria;
import com.talent.management.features.placement_test.entity.PlacementLevel;
import com.talent.management.features.placement_test.entity.PlacementSchedule;
import com.talent.management.features.placement_test.entity.PlacementScheduleStatus;
import com.talent.management.features.placement_test.mapper.PlacementScheduleMapper;
import com.talent.management.features.placement_test.repository.PlacementCriteriaRepository;
import com.talent.management.features.placement_test.repository.PlacementScheduleCriteriaScoreRepository;
import com.talent.management.features.placement_test.repository.PlacementScheduleRepository;
import com.talent.management.features.placement_test.repository.PlacementTestRepository;
import com.talent.management.features.placement_test.service.impl.PlacementTestServiceImpl;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.EnrollmentRequest;
import com.talent.management.shared.entity.Program;
import com.talent.management.shared.entity.Student;
import com.talent.management.shared.enums.EnrollmentRequestStatus;
import com.talent.management.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlacementAssessmentFlowTest {
    @Mock PlacementScheduleRepository scheduleRepository;
    @Mock PlacementTestRepository placementTestRepository;
    @Mock StudentRepository studentRepository;
    @Mock UserRepository userRepository;
    @Mock PlacementScheduleMapper scheduleMapper;
    @Mock EnrollmentRequestRepository enrollmentRequestRepository;
    @Mock CourseRepository courseRepository;
    @Mock PlacementCriteriaRepository criteriaRepository;
    @Mock PlacementScheduleCriteriaScoreRepository criteriaScoreRepository;
    @InjectMocks PlacementTestServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void injectCriteriaRepositories() {
        ReflectionTestUtils.setField(service, "criteriaRepository", criteriaRepository);
        ReflectionTestUtils.setField(service, "criteriaScoreRepository", criteriaScoreRepository);
    }

    @Test
    void assessmentRejectsACourseFromAnotherProgramInTheSameSubjectGroup() {
        Program piano = Program.builder().id(1L).placementSubject("DAN").build();
        Program guitar = Program.builder().id(2L).placementSubject("DAN").build();
        var schedule = PlacementSchedule.builder().program(piano).subject("DAN").build();
        when(scheduleRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(schedule));
        when(courseRepository.findById(4L)).thenReturn(Optional.of(Course.builder().id(4L).program(guitar).build()));

        assertThatThrownBy(() -> service.saveAssessment(9L,
                PlacementAssessmentRequest.builder().recommendedCourseId(4L).build(), "teacher"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("phải thuộc môn");
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void assessedLinkedScheduleProvidesRecommendationAndReadiesStaffRequest() {
        Student student = Student.builder().id(8L).build();
        EnrollmentRequest enrollmentRequest = EnrollmentRequest.builder().id(22L).student(student)
                .placementRequested(true).status(EnrollmentRequestStatus.WAITING_PLACEMENT).build();
        PlacementSchedule schedule = PlacementSchedule.builder().id(9L).enrollmentRequest(enrollmentRequest)
                .testDate(LocalDateTime.now()).build();
        Course course = Course.builder().id(2L).name("Piano Sơ Cấp").build();
        when(scheduleRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(schedule));
        when(scheduleRepository.save(schedule)).thenReturn(schedule);
        when(courseRepository.findById(2L)).thenReturn(Optional.of(course));

        PlacementAssessmentRequest assessment = PlacementAssessmentRequest.builder().score(38)
                .recommendedLevel(PlacementLevel.BEGINNER).recommendedCourseId(2L)
                .teacherNote("Sơ cấp").build();
        service.saveAssessment(9L, assessment, null);

        assertThat(schedule.getStatus()).isEqualTo(PlacementScheduleStatus.COMPLETED);
        assertThat(enrollmentRequest.getStatus()).isEqualTo(EnrollmentRequestStatus.READY_FOR_ASSIGNMENT);
        assertThat(enrollmentRequest.getPreferredCourse()).isSameAs(course);
        verify(enrollmentRequestRepository).save(enrollmentRequest);

        when(studentRepository.existsById(8L)).thenReturn(true);
        when(scheduleRepository.findFirstByEnrollmentRequestStudentIdOrderByTestDateDescIdDesc(8L))
                .thenReturn(Optional.of(schedule));
        var recommendation = service.getLatestRecommendation(8L);
        assertThat(recommendation.recommendationAvailable()).isTrue();
        assertThat(recommendation.score()).isEqualTo(3.8);
        assertThat(recommendation.recommendedCourseId()).isEqualTo(2L);
        assertThat(recommendation.recommendedLevel()).isEqualTo("BEGINNER");
        assertThat(recommendation.teacherNote()).isEqualTo("Sơ cấp");
        verifyNoInteractions(placementTestRepository);
    }

    @Test
    void legacyCompletedScheduleShowsScoreButWaitsForExplicitCourse() {
        EnrollmentRequest enrollmentRequest = EnrollmentRequest.builder().id(22L)
                .status(EnrollmentRequestStatus.WAITING_PLACEMENT).build();
        PlacementSchedule schedule = PlacementSchedule.builder().id(9L).enrollmentRequest(enrollmentRequest)
                .status(PlacementScheduleStatus.COMPLETED).score(38)
                .recommendedLevel(PlacementLevel.BEGINNER).build();
        when(studentRepository.existsById(8L)).thenReturn(true);
        when(scheduleRepository.findFirstByEnrollmentRequestStudentIdOrderByTestDateDescIdDesc(8L))
                .thenReturn(Optional.of(schedule));

        var recommendation = service.getLatestRecommendation(8L);
        assertThat(recommendation.placementStatus()).isEqualTo("COMPLETED");
        assertThat(recommendation.score()).isEqualTo(3.8);
        assertThat(recommendation.recommendationAvailable()).isFalse();
        assertThat(enrollmentRequest.getStatus()).isEqualTo(EnrollmentRequestStatus.WAITING_PLACEMENT);
        verifyNoInteractions(placementTestRepository);
    }

    @Test
    void linkedAssessmentWithoutCourseDoesNotOverwriteExistingResult() {
        PlacementSchedule schedule = PlacementSchedule.builder().id(9L)
                .enrollmentRequest(EnrollmentRequest.builder().id(22L).build())
                .status(PlacementScheduleStatus.COMPLETED).score(38).build();
        when(scheduleRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(schedule));
        PlacementAssessmentRequest assessment = PlacementAssessmentRequest.builder().score(40)
                .recommendedLevel(PlacementLevel.BEGINNER).build();

        assertThatThrownBy(() -> service.saveAssessment(9L, assessment, null))
                .isInstanceOf(BusinessException.class);
        assertThat(schedule.getScore()).isEqualTo(38);
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void cancelScheduleMarksCancelledAndUnlinksWaitingEnrollmentRequest() {
        EnrollmentRequest enrollmentRequest = EnrollmentRequest.builder().id(22L)
                .status(EnrollmentRequestStatus.WAITING_PLACEMENT).build();
        PlacementSchedule schedule = PlacementSchedule.builder().id(9L)
                .enrollmentRequest(enrollmentRequest)
                .status(PlacementScheduleStatus.SCHEDULED).build();
        when(scheduleRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(schedule));
        when(scheduleRepository.save(schedule)).thenReturn(schedule);

        service.cancelSchedule(9L, "Học viên bận đột xuất", "staff");

        assertThat(schedule.getStatus()).isEqualTo(PlacementScheduleStatus.CANCELLED);
        assertThat(schedule.getCancelReason()).isEqualTo("Học viên bận đột xuất");
        assertThat(schedule.getCancelledAt()).isNotNull();
        assertThat(schedule.getEnrollmentRequest()).isNull();
        verify(scheduleRepository).save(schedule);
    }

    @Test
    void cancelScheduleRequiresReason() {
        assertThatThrownBy(() -> service.cancelSchedule(9L, "   ", "staff"))
                .isInstanceOf(BusinessException.class);
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void completedScheduleCannotBeCancelled() {
        PlacementSchedule schedule = PlacementSchedule.builder().id(9L)
                .status(PlacementScheduleStatus.COMPLETED).score(85).build();
        when(scheduleRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> service.cancelSchedule(9L, "Hủy nhầm", "staff"))
                .isInstanceOf(BusinessException.class);
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void cancelledScheduleCannotBeAssessedOrCancelledAgain() {
        PlacementSchedule schedule = PlacementSchedule.builder().id(9L)
                .status(PlacementScheduleStatus.CANCELLED).cancelReason("Đã đổi lịch").build();
        when(scheduleRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(schedule));
        assertThatThrownBy(() -> service.saveAssessment(9L,
                PlacementAssessmentRequest.builder().score(80).build(), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("đã bị hủy");
        assertThatThrownBy(() -> service.cancelSchedule(9L, "Hủy lại", "staff"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("đã được hủy");
        assertThat(schedule.getCancelReason()).isEqualTo("Đã đổi lịch");
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void teacherCannotCancelAnotherSubjectsSchedule() {
        PlacementSchedule schedule = PlacementSchedule.builder().id(9L)
                .subject("MUA").status(PlacementScheduleStatus.SCHEDULED).build();
        when(scheduleRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(schedule));
        when(userRepository.findByUsername("teacher")).thenReturn(Optional.of(
                com.talent.management.shared.entity.User.builder()
                        .role(com.talent.management.shared.enums.Role.TEACHER).subject("DAN").build()));
        when(scheduleMapper.normalizeSubject("DAN")).thenReturn("DAN");
        when(scheduleMapper.normalizeSubject("MUA")).thenReturn("MUA");
        assertThatThrownBy(() -> service.cancelSchedule(9L, "Đổi lịch", "teacher"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("không có quyền hủy");
        assertThat(schedule.getStatus()).isEqualTo(PlacementScheduleStatus.SCHEDULED);
        verify(scheduleRepository, never()).save(any());
    }

    private PlacementAssessmentRequest criteriaRequest(List<PlacementCriteriaScoreItemRequest> items) {
        when(scheduleRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(
                PlacementSchedule.builder().id(9L).subject("DAN").testDate(LocalDateTime.now()).build()));
        return PlacementAssessmentRequest.builder().score(0)
                .recommendedLevel(PlacementLevel.INTERMEDIATE).criteriaScores(items).build();
    }

    @Test
    void assessmentRejectsUnknownCriteriaBeforeSaving() {
        var request = criteriaRequest(List.of(PlacementCriteriaScoreItemRequest.builder().criteriaId(99L).score(8.0).build()));
        assertThatThrownBy(() -> service.saveAssessment(9L, request, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("không tồn tại");
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void assessmentRejectsCriteriaFromAnotherSubject() {
        when(criteriaRepository.findById(1L)).thenReturn(Optional.of(
                PlacementCriteria.builder().id(1L).subject("MUA").criteriaName("Độ dẻo").build()));
        var request = criteriaRequest(List.of(PlacementCriteriaScoreItemRequest.builder().criteriaId(1L).score(8.0).build()));
        assertThatThrownBy(() -> service.saveAssessment(9L, request, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("không thuộc bộ môn");
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void assessmentRejectsDuplicateCriteriaBeforeCalculatingAverage() {
        when(criteriaRepository.findById(1L)).thenReturn(Optional.of(
                PlacementCriteria.builder().id(1L).subject("DAN").criteriaName("Cảm âm").build()));
        var item = PlacementCriteriaScoreItemRequest.builder().criteriaId(1L).score(8.0).build();
        var request = criteriaRequest(List.of(item, item));
        assertThatThrownBy(() -> service.saveAssessment(9L, request, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("trùng tiêu chí");
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void assessmentRejectsInactiveCriteriaNotPreviouslyUsedBySchedule() {
        when(criteriaRepository.findById(1L)).thenReturn(Optional.of(
                PlacementCriteria.builder().id(1L).subject("DAN").criteriaName("Cảm âm").active(false).build()));
        var request = criteriaRequest(List.of(PlacementCriteriaScoreItemRequest.builder().criteriaId(1L).score(8.0).build()));
        assertThatThrownBy(() -> service.saveAssessment(9L, request, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ngưng áp dụng");
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void assessmentRejectsMissingAndNonFiniteCriteriaScores() {
        var items = new java.util.ArrayList<PlacementCriteriaScoreItemRequest>();
        items.add(null);
        items.add(PlacementCriteriaScoreItemRequest.builder().build());
        items.add(PlacementCriteriaScoreItemRequest.builder().score(Double.NaN).build());
        items.add(PlacementCriteriaScoreItemRequest.builder().score(Double.POSITIVE_INFINITY).build());
        for (var item : items) {
            var request = criteriaRequest(java.util.Collections.singletonList(item));
            assertThatThrownBy(() -> service.saveAssessment(9L, request, null))
                    .isInstanceOf(BusinessException.class);
        }
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void deletingUsedCriteriaSoftDeletesToPreserveHistoricalAssessments() {
        ReflectionTestUtils.setField(service, "criteriaRepository", criteriaRepository);
        ReflectionTestUtils.setField(service, "criteriaScoreRepository", criteriaScoreRepository);
        PlacementCriteria criteria = PlacementCriteria.builder()
                .id(11L).subject("DAN").criteriaName("1. Cảm âm").displayOrder(1).active(true).build();
        when(criteriaRepository.findById(11L)).thenReturn(Optional.of(criteria));
        when(criteriaScoreRepository.existsByCriteriaId(11L)).thenReturn(true);

        service.deleteCriteria(11L);

        assertThat(criteria.getActive()).isFalse();
        verify(criteriaRepository).save(criteria);
        verify(criteriaRepository, never()).delete(any());
    }

    @Test
    void assessmentWithCriteriaScoresCalculatesNormalizedScoreAndStoresSnapshots() {
        ReflectionTestUtils.setField(service, "criteriaRepository", criteriaRepository);
        ReflectionTestUtils.setField(service, "criteriaScoreRepository", criteriaScoreRepository);
        PlacementSchedule schedule = PlacementSchedule.builder().id(9L).testDate(LocalDateTime.now()).build();
        PlacementCriteria c1 = PlacementCriteria.builder().id(1L).subject("DAN").criteriaName("Cảm âm").displayOrder(1).build();
        PlacementCriteria c2 = PlacementCriteria.builder().id(2L).subject("DAN").criteriaName("Nhịp phách").displayOrder(2).build();
        when(scheduleRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(schedule));
        when(scheduleRepository.save(schedule)).thenReturn(schedule);
        when(criteriaRepository.findById(1L)).thenReturn(Optional.of(c1));
        when(criteriaRepository.findById(2L)).thenReturn(Optional.of(c2));

        PlacementAssessmentRequest request = PlacementAssessmentRequest.builder()
                .score(0)
                .recommendedLevel(PlacementLevel.INTERMEDIATE)
                .criteriaScores(List.of(
                        PlacementCriteriaScoreItemRequest.builder().criteriaId(1L).score(8.0).build(),
                        PlacementCriteriaScoreItemRequest.builder().criteriaId(2L).score(9.0).build()
                ))
                .build();

        service.saveAssessment(9L, request, null);

        assertThat(schedule.getScore()).isEqualTo(85);
        verify(criteriaScoreRepository).deleteByScheduleId(9L);
        verify(criteriaScoreRepository).saveAll(argThat(items -> {
            var list = (List<?>) items;
            return list.size() == 2;
        }));
    }
}
