package com.talent.management.features.course_enrollment.service;

import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.features.course_enrollment.dto.request.EnrollmentDecisionRequest;
import com.talent.management.features.course_enrollment.dto.request.EnrollmentRequestCreateRequest;
import com.talent.management.features.course_enrollment.dto.response.ClassRecommendationResponse;
import com.talent.management.features.course_enrollment.dto.response.ClassResponse;
import com.talent.management.shared.entity.EnrollmentRequest;
import com.talent.management.features.course_enrollment.enums.EnrollmentDecision;
import com.talent.management.shared.enums.EnrollmentRequestStatus;
import com.talent.management.features.course_enrollment.mapper.CourseEnrollmentMapper;
import com.talent.management.features.course_enrollment.repository.ClassRepository;
import com.talent.management.features.course_enrollment.repository.CourseRepository;
import com.talent.management.features.course_enrollment.repository.EnrollmentRepository;
import com.talent.management.features.course_enrollment.repository.EnrollmentRequestRepository;
import com.talent.management.features.placement_test.dto.response.PlacementRecommendationResponse;
import com.talent.management.features.placement_test.service.PlacementTestService;
import com.talent.management.features.placement_test.repository.PlacementScheduleRepository;
import com.talent.management.features.tuition_payment.repository.InvoiceRepository;
import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.Enrollment;
import com.talent.management.shared.entity.Invoice;
import com.talent.management.shared.entity.Student;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.ClassStatus;
import com.talent.management.shared.enums.EnrollmentStatus;
import com.talent.management.shared.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

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

    @Mock
    private PlacementScheduleRepository placementScheduleRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private CourseEnrollmentService service;

    private Student child;
    private User parent;
    private Course course;
    private ClassEntity recommendedClass;
    private ClassResponse recommendedClassResponse;

    @BeforeEach
    void setUp() {
        parent = User.builder().id(10L).fullName("Parent").build();
        child = Student.builder()
                .id(20L)
                .parent(parent)
                .fullName("Nguyen Van An")
                .build();

        course = Course.builder()
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

        lenient().when(currentUserService.getCurrentUser()).thenReturn(parent);
        lenient().when(studentRepository.findByIdAndParentId(20L, 10L)).thenReturn(Optional.of(child));
        lenient().when(classRepository.findByStatusOrderByStartDateAsc(ClassStatus.OPEN))
                .thenReturn(List.of(recommendedClass));
        lenient().when(mapper.toClassResponse(recommendedClass)).thenReturn(recommendedClassResponse);
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

    @Test
    void createsReadyRequestWithoutAssigningClassWhenPlacementIsSkipped() {
        when(courseRepository.findById(30L)).thenReturn(Optional.of(course));

        service.submitRequest(new EnrollmentRequestCreateRequest(20L, 30L, false, "Weekend"));

        ArgumentCaptor<EnrollmentRequest> captor = ArgumentCaptor.forClass(EnrollmentRequest.class);
        verify(requestRepository).save(captor.capture());
        EnrollmentRequest saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(EnrollmentRequestStatus.READY_FOR_ASSIGNMENT);
        assertThat(saved.getPreferredCourse()).isSameAs(course);
        assertThat(saved.getClassEntity()).isNull();
        assertThat(saved.isPlacementRequested()).isFalse();
    }

    @Test
    void createsWaitingRequestWhenPlacementHasNoResult() {
        when(placementTestService.getLatestRecommendation(20L))
                .thenReturn(PlacementRecommendationResponse.notAvailable(20L));

        service.submitRequest(new EnrollmentRequestCreateRequest(20L, null, true, null));

        ArgumentCaptor<EnrollmentRequest> captor = ArgumentCaptor.forClass(EnrollmentRequest.class);
        verify(requestRepository).save(captor.capture());
        EnrollmentRequest saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(EnrollmentRequestStatus.WAITING_PLACEMENT);
        assertThat(saved.getPreferredCourse()).isNull();
        assertThat(saved.getClassEntity()).isNull();
        assertThat(saved.isPlacementRequested()).isTrue();
    }

    @Test
    void staffAssignmentCreatesPendingPaymentWithoutIncreasingClassSize() {
        User staff = User.builder().id(99L).fullName("Staff").build();
        EnrollmentRequest request = EnrollmentRequest.builder()
                .id(50L)
                .student(child)
                .preferredCourse(course)
                .requestedBy(parent)
                .status(EnrollmentRequestStatus.READY_FOR_ASSIGNMENT)
                .build();
        when(currentUserService.getCurrentUser()).thenReturn(staff);
        when(requestRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(request));
        when(classRepository.findById(40L)).thenReturn(Optional.of(recommendedClass));
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(Invoice.builder().id(70L).build());

        service.decide(50L, new EnrollmentDecisionRequest(EnrollmentDecision.APPROVE, 40L, "Assigned"));

        ArgumentCaptor<Enrollment> enrollmentCaptor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository).save(enrollmentCaptor.capture());
        assertThat(enrollmentCaptor.getValue().getStatus()).isEqualTo(EnrollmentStatus.PENDING_PAYMENT);
        assertThat(enrollmentCaptor.getValue().getRegisteredByUser()).isSameAs(staff);
        assertThat(request.getStatus()).isEqualTo(EnrollmentRequestStatus.PENDING_PAYMENT);
        assertThat(request.getClassEntity()).isSameAs(recommendedClass);
        assertThat(recommendedClass.getCurrentStudents()).isEqualTo(10);
        verify(classRepository, never()).save(any(ClassEntity.class));
    }

    @Test
    void parentCanCancelReadyRequestWithoutPlacement() {
        EnrollmentRequest request = EnrollmentRequest.builder().id(51L).student(child)
                .requestedBy(parent).status(EnrollmentRequestStatus.READY_FOR_ASSIGNMENT).build();
        when(requestRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(request));

        service.cancelRequest(51L);

        assertThat(request.getStatus()).isEqualTo(EnrollmentRequestStatus.CANCELLED);
        verify(requestRepository).save(request);
        verifyNoInteractions(placementScheduleRepository);
    }

    @Test
    void parentCanCancelPlacementRequestBeforeScheduling() {
        EnrollmentRequest request = EnrollmentRequest.builder().id(52L).student(child)
                .requestedBy(parent).placementRequested(true)
                .status(EnrollmentRequestStatus.WAITING_PLACEMENT).build();
        when(requestRepository.findByIdForUpdate(52L)).thenReturn(Optional.of(request));
        when(placementTestService.getLatestRecommendation(20L))
                .thenReturn(PlacementRecommendationResponse.notAvailable(20L));

        service.cancelRequest(52L);

        assertThat(request.getStatus()).isEqualTo(EnrollmentRequestStatus.CANCELLED);
        verify(requestRepository).save(request);
    }

    @Test
    void parentCannotCancelPlacementRequestAfterScheduling() {
        EnrollmentRequest request = EnrollmentRequest.builder().id(53L).student(child)
                .requestedBy(parent).placementRequested(true)
                .status(EnrollmentRequestStatus.WAITING_PLACEMENT).build();
        when(requestRepository.findByIdForUpdate(53L)).thenReturn(Optional.of(request));
        when(placementScheduleRepository.existsByEnrollmentRequestId(53L)).thenReturn(true);

        assertThatThrownBy(() -> service.cancelRequest(53L)).isInstanceOf(ResponseStatusException.class);
        assertThat(request.getStatus()).isEqualTo(EnrollmentRequestStatus.WAITING_PLACEMENT);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void parentCannotCancelPlacementRequestAfterResultExists() {
        EnrollmentRequest request = EnrollmentRequest.builder().id(54L).student(child)
                .requestedBy(parent).placementRequested(true)
                .status(EnrollmentRequestStatus.WAITING_PLACEMENT).build();
        when(requestRepository.findByIdForUpdate(54L)).thenReturn(Optional.of(request));
        when(placementTestService.getLatestRecommendation(20L)).thenReturn(
                new PlacementRecommendationResponse(20L, "COMPLETED", false, 8.0, null,
                        null, null, null, LocalDateTime.now()));

        assertThatThrownBy(() -> service.cancelRequest(54L)).isInstanceOf(ResponseStatusException.class);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void anotherParentCannotCancelRequest() {
        User otherParent = User.builder().id(99L).build();
        EnrollmentRequest request = EnrollmentRequest.builder().id(55L).student(child)
                .requestedBy(otherParent).status(EnrollmentRequestStatus.READY_FOR_ASSIGNMENT).build();
        when(requestRepository.findByIdForUpdate(55L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancelRequest(55L)).isInstanceOf(ResponseStatusException.class);
        verify(requestRepository, never()).save(any());
    }
}
