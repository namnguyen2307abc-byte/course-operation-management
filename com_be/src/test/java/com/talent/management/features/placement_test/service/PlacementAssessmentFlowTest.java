package com.talent.management.features.placement_test.service;

import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.course_enrollment.repository.CourseRepository;
import com.talent.management.features.course_enrollment.repository.EnrollmentRequestRepository;
import com.talent.management.features.placement_test.dto.request.PlacementAssessmentRequest;
import com.talent.management.features.placement_test.entity.PlacementLevel;
import com.talent.management.features.placement_test.entity.PlacementSchedule;
import com.talent.management.features.placement_test.entity.PlacementScheduleStatus;
import com.talent.management.features.placement_test.mapper.PlacementScheduleMapper;
import com.talent.management.features.placement_test.repository.PlacementScheduleRepository;
import com.talent.management.features.placement_test.repository.PlacementTestRepository;
import com.talent.management.features.placement_test.service.impl.PlacementTestServiceImpl;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.EnrollmentRequest;
import com.talent.management.shared.entity.Student;
import com.talent.management.shared.enums.EnrollmentRequestStatus;
import com.talent.management.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
    @InjectMocks PlacementTestServiceImpl service;

    @Test
    void assessedLinkedScheduleProvidesRecommendationAndReadiesStaffRequest() {
        Student student = Student.builder().id(8L).build();
        EnrollmentRequest enrollmentRequest = EnrollmentRequest.builder().id(22L).student(student)
                .placementRequested(true).status(EnrollmentRequestStatus.WAITING_PLACEMENT).build();
        PlacementSchedule schedule = PlacementSchedule.builder().id(9L).enrollmentRequest(enrollmentRequest)
                .testDate(LocalDateTime.now()).build();
        Course course = Course.builder().id(2L).name("Piano Sơ Cấp").build();
        when(scheduleRepository.findById(9L)).thenReturn(Optional.of(schedule));
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
        when(scheduleRepository.findById(9L)).thenReturn(Optional.of(schedule));
        PlacementAssessmentRequest assessment = PlacementAssessmentRequest.builder().score(40)
                .recommendedLevel(PlacementLevel.BEGINNER).build();

        assertThatThrownBy(() -> service.saveAssessment(9L, assessment, null))
                .isInstanceOf(BusinessException.class);
        assertThat(schedule.getScore()).isEqualTo(38);
        verify(scheduleRepository, never()).save(any());
    }
}
