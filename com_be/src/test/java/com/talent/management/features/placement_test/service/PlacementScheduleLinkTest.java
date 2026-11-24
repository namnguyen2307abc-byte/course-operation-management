package com.talent.management.features.placement_test.service;

import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.course_management.repository.ProgramRepository;
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
import com.talent.management.shared.entity.Program;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.exception.BusinessException;
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
    @Mock private ProgramRepository programRepository;
    @InjectMocks private PlacementTestServiceImpl service;

    private CreatePlacementScheduleRequest input() {
        return CreatePlacementScheduleRequest.builder()
                .enrollmentRequestId(50L)
                .studentName("User entered name")
                .title("Placement")
                .roomName("Room 1")
                .testDate(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0))
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
        Program piano = Program.builder().id(40L).name("Piano").placementSubject("DAN").build();
        request.setRequestedProgram(piano);
        PlacementSchedule schedule = new PlacementSchedule();
        when(programRepository.findById(40L)).thenReturn(Optional.of(piano));
        when(scheduleMapper.toEntity(any())).thenReturn(schedule);
        when(enrollmentRequestRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(request));
        when(scheduleRepository.save(schedule)).thenReturn(schedule);

        service.createSchedule(input(), "staff");

        assertThat(schedule.getEnrollmentRequest()).isSameAs(request);
        assertThat(schedule.getStudentName()).isEqualTo("Nguyen Van An");
        assertThat(schedule.getParent()).isSameAs(request.getRequestedBy());
        assertThat(schedule.getProgram()).isSameAs(piano);
        assertThat(schedule.getSubject()).isEqualTo("DAN");
    }

    @Test
    void linkedScheduleCannotBeCreatedTwice() {
        when(enrollmentRequestRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(waitingRequest()));
        when(scheduleRepository.existsByEnrollmentRequestId(50L)).thenReturn(true);

        assertThatThrownBy(() -> service.createSchedule(input(), "staff"))
                .isInstanceOf(ResponseStatusException.class);
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void linkedScheduleRejectsChangingTheProgramOnTheRequest() {
        EnrollmentRequest request = waitingRequest();
        request.setRequestedProgram(Program.builder().id(40L).build());
        var input = input();
        input.setProgramId(41L);
        when(enrollmentRequestRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.createSchedule(input, "staff"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Môn kiểm tra phải trùng");
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void legacyRequestCanBeScheduledAfterStaffChoosesAProgram() {
        EnrollmentRequest request = waitingRequest();
        Program guitar = Program.builder().id(41L).name("Guitar").placementSubject("DAN").build();
        PlacementSchedule schedule = new PlacementSchedule();
        var input = input();
        input.setProgramId(41L);
        when(enrollmentRequestRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(request));
        when(programRepository.findById(41L)).thenReturn(Optional.of(guitar));
        when(scheduleMapper.toEntity(input)).thenReturn(schedule);
        when(scheduleRepository.save(schedule)).thenReturn(schedule);

        service.createSchedule(input, "staff");

        assertThat(request.getRequestedProgram()).isSameAs(guitar);
        assertThat(schedule.getProgram()).isSameAs(guitar);
    }

    @Test
    void independentScheduleRequiresAProgramWithPlacementConfiguration() {
        var input = input();
        input.setEnrollmentRequestId(null);
        assertThatThrownBy(() -> service.createSchedule(input, "staff"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Vui lòng chọn môn");

        input.setProgramId(42L);
        when(programRepository.findById(42L)).thenReturn(Optional.of(Program.builder().id(42L).name("Cờ vua").build()));
        assertThatThrownBy(() -> service.createSchedule(input, "staff"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("chưa hỗ trợ");
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void scheduleRejectsAppointmentOutsideWorkingHours() {
        var earlyMorning = input();
        earlyMorning.setTestDate(LocalDateTime.now().plusDays(1).withHour(3).withMinute(0));
        assertThatThrownBy(() -> service.createSchedule(earlyMorning, "staff"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("08:00 - 17:30");

        var midnight = input();
        midnight.setTestDate(LocalDateTime.now().plusDays(1).withHour(0).withMinute(0));
        assertThatThrownBy(() -> service.createSchedule(midnight, "staff"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("08:00 - 17:30");

        var lateNight = input();
        lateNight.setTestDate(LocalDateTime.now().plusDays(1).withHour(20).withMinute(30));
        assertThatThrownBy(() -> service.createSchedule(lateNight, "staff"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("08:00 - 17:30");

        verify(scheduleRepository, never()).save(any());
    }
}
