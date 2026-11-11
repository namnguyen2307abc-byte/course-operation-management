package com.talent.management.features.placement_test.service;

import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.shared.entity.EnrollmentRequest;
import com.talent.management.shared.enums.EnrollmentRequestStatus;
import com.talent.management.features.course_enrollment.repository.EnrollmentRequestRepository;
import com.talent.management.features.placement_test.dto.request.CreatePlacementScheduleRequest;
import com.talent.management.features.placement_test.entity.PlacementSchedule;
import com.talent.management.features.placement_test.mapper.PlacementScheduleMapper;
import com.talent.management.features.placement_test.repository.PlacementScheduleRepository;
import com.talent.management.features.placement_test.repository.PlacementTestRepository;
import com.talent.management.features.placement_test.service.impl.PlacementTestServiceImpl;
import com.talent.management.shared.entity.Student;
import com.talent.management.shared.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlacementScheduleLinkTest {

    @Mock private PlacementScheduleRepository scheduleRepository;
    @Mock private PlacementTestRepository placementTestRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private UserRepository userRepository;
    @Mock private PlacementScheduleMapper scheduleMapper;
    @Mock private EnrollmentRequestRepository enrollmentRequestRepository;
    @InjectMocks private PlacementTestServiceImpl service;

    private CreatePlacementScheduleRequest input() {
        return CreatePlacementScheduleRequest.builder()
                .enrollmentRequestId(50L)
                .studentName("User entered name")
                .title("Placement")
                .roomName("Room 1")
                .testDate(LocalDateTime.now().plusDays(1))
                .build();
    }

    private EnrollmentRequest waitingRequest() {
        User parent = User.builder().id(10L).build();
        Student child = Student.builder().id(20L).fullName("Nguyen Van An").parent(parent).build();
        return EnrollmentRequest.builder().id(50L).student(child).requestedBy(parent)
                .placementRequested(true).status(EnrollmentRequestStatus.WAITING_PLACEMENT).build();
    }

    @Test
    void linkedScheduleUsesStudentAndParentFromRequest() {
        EnrollmentRequest request = waitingRequest();
        PlacementSchedule schedule = new PlacementSchedule();
        when(scheduleMapper.toEntity(any())).thenReturn(schedule);
        when(enrollmentRequestRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(request));
        when(scheduleRepository.save(schedule)).thenReturn(schedule);

        service.createSchedule(input(), "staff");

        assertThat(schedule.getEnrollmentRequest()).isSameAs(request);
        assertThat(schedule.getStudentName()).isEqualTo("Nguyen Van An");
        assertThat(schedule.getParent()).isSameAs(request.getRequestedBy());
    }

    @Test
    void linkedScheduleCannotBeCreatedTwice() {
        when(scheduleMapper.toEntity(any())).thenReturn(new PlacementSchedule());
        when(enrollmentRequestRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(waitingRequest()));
        when(scheduleRepository.existsByEnrollmentRequestId(50L)).thenReturn(true);

        assertThatThrownBy(() -> service.createSchedule(input(), "staff"))
                .isInstanceOf(ResponseStatusException.class);
        verify(scheduleRepository, never()).save(any());
    }
}
