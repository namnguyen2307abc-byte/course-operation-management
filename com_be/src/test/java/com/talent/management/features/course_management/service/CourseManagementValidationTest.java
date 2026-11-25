package com.talent.management.features.course_management.service;

import com.talent.management.features.course_management.dto.request.CreateCourseRequest;
import com.talent.management.features.course_management.dto.request.UpdateCourseRequest;
import com.talent.management.features.course_management.repository.CourseManagementRepository;
import com.talent.management.features.course_management.repository.ProgramRepository;
import com.talent.management.features.course_management.service.impl.CourseManagementServiceImpl;
import com.talent.management.features.placement_test.entity.PlacementLevel;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.Program;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseManagementValidationTest {
    @Mock CourseManagementRepository courses;
    @Mock ProgramRepository programs;
    @InjectMocks CourseManagementServiceImpl service;

    @ParameterizedTest
    @CsvSource({"-1,số âm", "1.5,số thập phân", "0,lớn hơn 0", "61,60", "13,12", "20,12"})
    void createAndUpdateRejectInvalidSessionCounts(String raw, String message) {
        var sessions = new BigDecimal(raw);
        assertBadRequest(() -> service.createCourse(CreateCourseRequest.builder()
                .totalSessions(sessions).tuitionFee(new BigDecimal("30000")).build()), message);
        assertBadRequest(() -> service.updateCourse(1L, UpdateCourseRequest.builder()
                .totalSessions(sessions).tuitionFee(new BigDecimal("30000")).build()), message);
        verifyNoInteractions(courses, programs);
    }

    @ParameterizedTest
    @CsvSource({"-1,số âm", "1.5,số thập phân", "0,lớn hơn 0", "10000000000,50.000 đến 1.000.000"})
    void createAndUpdateRejectInvalidUnitPrices(String raw, String message) {
        var fee = new BigDecimal(raw);
        assertBadRequest(() -> service.createCourse(CreateCourseRequest.builder()
                .totalSessions(new BigDecimal("12")).tuitionPerSession(fee).build()), message);
        assertBadRequest(() -> service.updateCourse(1L, UpdateCourseRequest.builder()
                .totalSessions(new BigDecimal("12")).tuitionPerSession(fee).build()), message);
        verifyNoInteractions(courses, programs);
    }

    @Test void rejectsMissingTuition() {
        assertBadRequest(() -> service.createCourse(CreateCourseRequest.builder().build()), "không được để trống");
        verifyNoInteractions(courses, programs);
    }

    @ParameterizedTest
    @CsvSource({"12,600000", "24,1200000", "36,1800000", "48,2400000", "60,3000000"})
    void calculatesTotalTuitionAndIgnoresClientTotal(int sessions, String total) {
        when(programs.findById(1L)).thenReturn(Optional.of(Program.builder().id(1L).code("PIANO").name("Piano").build()));
        when(courses.save(any())).thenAnswer(inv -> {
            Course course = inv.getArgument(0); course.setId(1L); return course;
        });
        var result = service.createCourse(CreateCourseRequest.builder().programId(1L)
                .code("PIA-TEST").name("Piano").level(PlacementLevel.BEGINNER)
                .totalSessions(BigDecimal.valueOf(sessions)).tuitionPerSession(new BigDecimal("50000.00"))
                .tuitionFee(BigDecimal.ONE).build());
        assertThat(result.durationWeeks()).isNull();
        assertThat(result.totalSessions()).isEqualTo(sessions);
        assertThat(result.tuitionFee()).isEqualByComparingTo(total);
        assertThat(result.tuitionPerSession()).isEqualByComparingTo("50000");
        verify(courses).save(argThat(c -> c.getDurationWeeks() == null && c.getTotalSessions() == sessions));
    }

    @Test void requiresUnitPriceEvenWhenClientSuppliesTotal() {
        assertBadRequest(() -> service.createCourse(CreateCourseRequest.builder()
                .totalSessions(new BigDecimal("12")).tuitionFee(new BigDecimal("360000")).build()), "Đơn giá");
        verifyNoInteractions(courses, programs);
    }

    @ParameterizedTest
    @CsvSource({"1", "49999", "1000001", "99999999999999999999999"})
    void rejectsUnitPricesOutsideAllowedRangeForCreateAndUpdate(String unitPrice) {
        assertBadRequest(() -> service.createCourse(CreateCourseRequest.builder()
                .totalSessions(new BigDecimal("12")).tuitionPerSession(new BigDecimal(unitPrice)).build()), "50.000 đến 1.000.000");
        assertBadRequest(() -> service.updateCourse(1L, UpdateCourseRequest.builder()
                .totalSessions(new BigDecimal("12")).tuitionPerSession(new BigDecimal(unitPrice)).build()), "50.000 đến 1.000.000");
        verifyNoInteractions(courses, programs);
    }

    @Test void acceptsUpperBoundaryAndCalculatesTotal() {
        when(programs.findById(1L)).thenReturn(Optional.of(Program.builder().id(1L).code("PIANO").build()));
        when(courses.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var result = service.createCourse(CreateCourseRequest.builder().programId(1L)
                .code("PIA-60").name("Piano").level(PlacementLevel.BEGINNER)
                .totalSessions(new BigDecimal("60")).tuitionPerSession(new BigDecimal("1000000")).build());
        assertThat(result.tuitionFee()).isEqualByComparingTo("60000000");
    }

    @Test void legacyReadDoesNotInventUnitPriceOrChangeTotal() {
        Course legacy = Course.builder().id(1L).program(Program.builder().id(1L).build())
                .totalSessions(20).tuitionFee(new BigDecimal("3900000")).build();
        when(courses.findById(1L)).thenReturn(Optional.of(legacy));
        var response = service.getCourseById(1L);
        assertThat(response.totalSessions()).isEqualTo(20);
        assertThat(response.tuitionFee()).isEqualByComparingTo("3900000");
        assertThat(response.tuitionPerSession()).isNull();
        verify(courses, never()).save(any());
    }

    @Test void updatingLegacyCourseRequiresExplicitPriceAndRecalculatesOnlyCourse() {
        Program program = Program.builder().id(1L).code("PIANO").build();
        Course legacy = Course.builder().id(1L).program(program).totalSessions(20)
                .tuitionFee(new BigDecimal("3900000")).build();
        when(courses.findById(1L)).thenReturn(Optional.of(legacy));
        when(programs.findById(1L)).thenReturn(Optional.of(program));
        when(courses.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var result = service.updateCourse(1L, UpdateCourseRequest.builder().programId(1L)
                .code("PIA-01").name("Piano").level(PlacementLevel.BEGINNER)
                .totalSessions(new BigDecimal("24")).tuitionPerSession(new BigDecimal("200000")).build());
        assertThat(result.totalSessions()).isEqualTo(24);
        assertThat(result.tuitionFee()).isEqualByComparingTo("4800000");
    }

    private void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String message) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getReason()).contains(message);
        });
    }
}
