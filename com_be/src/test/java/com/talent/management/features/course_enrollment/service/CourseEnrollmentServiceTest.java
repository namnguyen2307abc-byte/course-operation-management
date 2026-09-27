package com.talent.management.features.course_enrollment.service;

import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.features.course_enrollment.dto.response.ClassRecommendationResponse;
import com.talent.management.features.course_enrollment.dto.response.ClassResponse;
import com.talent.management.features.course_enrollment.mapper.CourseEnrollmentMapper;
import com.talent.management.features.course_enrollment.repository.ClassRepository;
import com.talent.management.features.course_enrollment.repository.CourseRepository;
import com.talent.management.features.course_enrollment.repository.EnrollmentRepository;
import com.talent.management.features.course_enrollment.repository.EnrollmentRequestRepository;
import com.talent.management.features.placement_test.dto.response.PlacementRecommendationResponse;
import com.talent.management.features.placement_test.service.PlacementTestService;
import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.Student;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.ClassStatus;
import com.talent.management.shared.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseEnrollmentServiceTest {

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private ClassRepository classRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private EnrollmentRequestRepository requestRepository;

    @Mock
    private CourseEnrollmentMapper mapper;

    @Mock
    private PlacementTestService placementTestService;

    @InjectMocks
    private CourseEnrollmentService service;

    private Student child;
    private ClassEntity recommendedClass;
    private ClassResponse recommendedClassResponse;

    @BeforeEach
    void setUp() {
        User parent = User.builder().id(10L).build();
        child = Student.builder()
                .id(20L)
                .parent(parent)
                .fullName("Nguyen Van An")
                .build();

        Course course = Course.builder()
                .id(30L)
                .name("English A2")
                .build();
        recommendedClass = ClassEntity.builder()
                .id(40L)
                .course(course)
                .classCode("ENG-A2-01")
                .className("English A2 - Evening")
                .maxStudents(20)
                .currentStudents(10)
                .status(ClassStatus.OPEN)
                .build();
        recommendedClassResponse = new ClassResponse(
                40L,
                30L,
                "English A2",
                "ENG-A2-01",
                "English A2 - Evening",
                null,
                1L,
                "Main branch",
                null,
                null,
                null,
                20,
                10,
                10,
                null,
                null,
                null,
                ClassStatus.OPEN
        );

        when(currentUserService.getCurrentUser()).thenReturn(parent);
        when(studentRepository.findByIdAndParentId(20L, 10L)).thenReturn(Optional.of(child));
        when(classRepository.findByStatusOrderByStartDateAsc(ClassStatus.OPEN))
                .thenReturn(List.of(recommendedClass));
        when(mapper.toClassResponse(recommendedClass)).thenReturn(recommendedClassResponse);
    }

    @Test
    void returnsRecommendedClassesWhenPlacementResultExists() {
        PlacementRecommendationResponse placement = new PlacementRecommendationResponse(
                20L,
                "COMPLETED",
                true,
                8.5,
                "A2",
                30L,
                "English A2",
                "Ready for A2",
                LocalDateTime.of(2026, 9, 27, 9, 0)
        );
        when(placementTestService.getLatestRecommendation(20L)).thenReturn(placement);

        ClassRecommendationResponse response = service.getClassRecommendations(20L);

        assertThat(response.recommendationAvailable()).isTrue();
        assertThat(response.recommendedCourseId()).isEqualTo(30L);
        assertThat(response.recommendedClasses()).containsExactly(recommendedClassResponse);
        assertThat(response.openClasses()).containsExactly(recommendedClassResponse);
    }

    @Test
    void returnsAllOpenClassesWhenPlacementResultDoesNotExist() {
        when(placementTestService.getLatestRecommendation(20L))
                .thenReturn(PlacementRecommendationResponse.notAvailable(20L));

        ClassRecommendationResponse response = service.getClassRecommendations(20L);

        assertThat(response.placementStatus()).isEqualTo("NOT_AVAILABLE");
        assertThat(response.recommendationAvailable()).isFalse();
        assertThat(response.recommendedClasses()).isEmpty();
        assertThat(response.openClasses()).containsExactly(recommendedClassResponse);
    }
}
