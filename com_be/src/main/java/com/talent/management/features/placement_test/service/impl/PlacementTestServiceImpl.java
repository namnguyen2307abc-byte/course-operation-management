package com.talent.management.features.placement_test.service.impl;

import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.course_management.repository.ProgramRepository;
import com.talent.management.shared.entity.EnrollmentRequest;
import com.talent.management.shared.enums.EnrollmentRequestStatus;
import com.talent.management.features.course_enrollment.repository.EnrollmentRequestRepository;
import com.talent.management.features.placement_test.dto.request.CreatePlacementScheduleRequest;
import com.talent.management.features.placement_test.dto.request.PlacementAssessmentRequest;
import com.talent.management.features.placement_test.dto.response.PlacementScheduleResponse;
import com.talent.management.features.placement_test.dto.response.PlacementRecommendationResponse;
import com.talent.management.features.placement_test.dto.response.PlacementProgramResponse;
import com.talent.management.features.placement_test.entity.PlacementSchedule;
import com.talent.management.features.placement_test.entity.PlacementScheduleStatus;
import com.talent.management.features.placement_test.mapper.PlacementScheduleMapper;
import com.talent.management.features.placement_test.repository.PlacementScheduleRepository;
import com.talent.management.features.placement_test.repository.PlacementTestRepository;
import com.talent.management.features.placement_test.service.PlacementTestService;
import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.features.course_enrollment.repository.CourseRepository;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.PlacementTest;
import com.talent.management.shared.entity.Program;
import com.talent.management.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlacementTestServiceImpl implements PlacementTestService {

    private static final Set<String> PLACEMENT_SUBJECTS = Set.of("DAN", "MUA", "VO");
    private final PlacementScheduleRepository scheduleRepository;
    private final PlacementTestRepository placementTestRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final PlacementScheduleMapper scheduleMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.talent.management.features.notification.service.NotificationService notificationService;
    private final EnrollmentRequestRepository enrollmentRequestRepository;
    private final CourseRepository courseRepository;
    private final ProgramRepository programRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PlacementProgramResponse> getPlacementPrograms() {
        return programRepository.findByPlacementSubjectIsNotNullOrderByNameAsc().stream()
                .filter(this::isPlacementEnabled)
                .map(program -> new PlacementProgramResponse(program.getId(), program.getName(), program.getPlacementSubject()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Program requirePlacementProgram(Long programId) {
        if (programId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn môn muốn kiểm tra");
        }
        return programRepository.findById(programId).filter(this::isPlacementEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Môn đã chọn chưa hỗ trợ Placement Test"));
    }

    private boolean isPlacementEnabled(Program program) {
        return program.getPlacementSubject() != null && PLACEMENT_SUBJECTS.contains(program.getPlacementSubject());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlacementScheduleResponse> getAllSchedules() {
        return getAllSchedules(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlacementScheduleResponse> getAllSchedules(String currentUsername) {
        if (currentUsername != null && !currentUsername.isBlank()) {
            var userOpt = userRepository.findByUsername(currentUsername);
            if (userOpt.isPresent() && userOpt.get().getRole() == com.talent.management.shared.enums.Role.TEACHER) {
                String teacherSubject = userOpt.get().getSubject();
                if (teacherSubject != null && !teacherSubject.isBlank()) {
                    return scheduleRepository.findBySubjectIgnoreCaseOrderByTestDateDesc(teacherSubject.trim())
                            .stream()
                            .map(scheduleMapper::toResponse)
                            .collect(Collectors.toList());
                }
            }
        }
        return scheduleRepository.findAllByOrderByTestDateDesc()
                .stream()
                .map(scheduleMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlacementScheduleResponse> getSchedulesForParent(String parentEmail) {
        return getSchedulesForParent(parentEmail, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlacementScheduleResponse> getSchedulesForParent(String parentEmail, String currentUsername) {
        if (parentEmail == null || parentEmail.isBlank()) {
            return getAllSchedules(currentUsername);
        }

        String normalizedEmail = parentEmail.trim().toLowerCase();
        if (currentUsername != null && !currentUsername.isBlank()) {
            var userOpt = userRepository.findByUsername(currentUsername);
            if (userOpt.isPresent() && userOpt.get().getRole() == com.talent.management.shared.enums.Role.TEACHER) {
                String teacherSubject = userOpt.get().getSubject();
                if (teacherSubject != null && !teacherSubject.isBlank()) {
                    return scheduleRepository.findBySubjectIgnoreCaseAndParentEmailOrderByTestDateAsc(teacherSubject.trim(), normalizedEmail)
                            .stream()
                            .map(scheduleMapper::toResponse)
                            .collect(Collectors.toList());
                }
            }
        }

        return scheduleRepository.findByParentEmailOrderByTestDateAsc(normalizedEmail)
                .stream()
                .map(scheduleMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PlacementScheduleResponse getScheduleById(Long id) {
        PlacementSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Lịch thi xếp lớp không tồn tại với ID: " + id));
        return scheduleMapper.toResponse(schedule);
    }

    @Override
    @Transactional
    public PlacementScheduleResponse saveAssessment(Long scheduleId, PlacementAssessmentRequest request, String teacherUsername) {
        PlacementSchedule schedule = scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new BusinessException("Lịch thi xếp lớp không tồn tại với ID: " + scheduleId));

        if (schedule.getStatus() == PlacementScheduleStatus.CANCELLED) {
            throw new BusinessException("Không thể chấm điểm cho đơn Placement Test đã bị hủy!");
        }

        EnrollmentRequest enrollmentRequest = schedule.getEnrollmentRequest();
        if (enrollmentRequest != null && request.getRecommendedCourseId() == null) {
            throw new BusinessException("Vui lòng chọn khóa học đề xuất cho yêu cầu ghi danh");
        }
        Course recommendedCourse = request.getRecommendedCourseId() == null ? null : courseRepository
                .findById(request.getRecommendedCourseId())
                .orElseThrow(() -> new BusinessException("Khóa học đề xuất không tồn tại"));
        if (schedule.getProgram() != null && recommendedCourse != null
                && !Objects.equals(schedule.getProgram().getId(), recommendedCourse.getProgram().getId())) {
            throw new BusinessException("Khóa học đề xuất phải thuộc môn của ca kiểm tra");
        }

        if (teacherUsername != null && !teacherUsername.isBlank()) {
            var teacherOpt = userRepository.findByUsername(teacherUsername);
            if (teacherOpt.isPresent()) {
                var teacher = teacherOpt.get();
                if (teacher.getRole() != com.talent.management.shared.enums.Role.TEACHER) {
                    throw new BusinessException("Chỉ tài khoản Giáo viên (TEACHER) mới có quyền chấm điểm đánh giá bài thi!");
                }
                String teacherSubject = teacher.getSubject();
                String scheduleSubject = schedule.getSubject() != null ? schedule.getSubject() : scheduleMapper.inferSubject(schedule.getTitle());
                if (teacherSubject != null && !teacherSubject.isBlank() && scheduleSubject != null && !scheduleSubject.isBlank()) {
                    String normTeacher = scheduleMapper.normalizeSubject(teacherSubject);
                    String normSchedule = scheduleMapper.normalizeSubject(scheduleSubject);
                    if (!normTeacher.equalsIgnoreCase(normSchedule)) {
                        throw new BusinessException("Bạn là giáo viên bộ môn " + teacherSubject + ", không có quyền chấm bài thi xếp lớp thuộc bộ môn " + scheduleSubject + "!");
                    }
                }
                schedule.setEvaluatedBy(teacher);
            }
        }

        schedule.setScore(request.getScore());
        schedule.setRecommendedLevel(request.getRecommendedLevel());
        schedule.setRecommendedCourse(recommendedCourse);
        schedule.setTeacherNote(request.getTeacherNote());
        schedule.setAudioUrl(request.getAudioUrl());
        schedule.setVideoUrl(request.getVideoUrl());
        schedule.setImageUrl(request.getImageUrl());
        schedule.setRecordUrl(request.getRecordUrl());
        schedule.setEvaluatedAt(LocalDateTime.now());
        schedule.setStatus(PlacementScheduleStatus.COMPLETED);

        PlacementSchedule saved = scheduleRepository.save(schedule);
        if (enrollmentRequest != null && enrollmentRequest.getStatus() == EnrollmentRequestStatus.WAITING_PLACEMENT) {
            enrollmentRequest.setPreferredCourse(recommendedCourse);
            enrollmentRequest.setStatus(EnrollmentRequestStatus.READY_FOR_ASSIGNMENT);
            enrollmentRequestRepository.save(enrollmentRequest);
        }
        if (notificationService != null) {
            String recCourseName = recommendedCourse != null ? recommendedCourse.getName() : "Theo đề xuất giáo viên";
            if (saved.getParent() != null) {
                notificationService.sendNotification(
                        saved.getParent(), null,
                        "Đã có kết quả đánh giá đầu vào (Placement Test)",
                        "Bé " + saved.getStudentName() + " đã hoàn thành bài kiểm tra đầu vào (Điểm: " + saved.getScore() + "). Khóa học đề xuất: " + recCourseName + ".",
                        "PLACEMENT_COMPLETED", saved.getId(), "/pages/course-enrollment.html");
            }
            notificationService.sendToRoles(
                    List.of(com.talent.management.shared.enums.Role.STAFF, com.talent.management.shared.enums.Role.ADMIN), null,
                    "Đã có kết quả Placement Test — Chờ xếp lớp",
                    "Học viên " + saved.getStudentName() + " đạt " + saved.getScore() + " điểm (Đề xuất: " + recCourseName + "), sẵn sàng xếp lớp.",
                    "PLACEMENT_COMPLETED", saved.getId(), "/pages/course-enrollment.html");
        }
        return scheduleMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PlacementScheduleResponse createSchedule(CreatePlacementScheduleRequest request, String parentUsername) {
        if (request.getTestDate() == null) {
            throw new BusinessException("Thời gian hẹn test không được để trống!");
        }
        if (request.getTestDate().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Thời gian hẹn test không được ở trong quá khứ tính từ thời điểm hiện tại! Vui lòng chọn thời gian từ hiện tại trở đi.");
        }

        EnrollmentRequest enrollmentRequest = null;
        Program program;
        if (request.getEnrollmentRequestId() != null) {
            enrollmentRequest = enrollmentRequestRepository
                    .findByIdForUpdate(request.getEnrollmentRequestId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đăng ký"));
            if (!enrollmentRequest.isPlacementRequested()
                    || enrollmentRequest.getStatus() != EnrollmentRequestStatus.WAITING_PLACEMENT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu không còn chờ xếp lịch Placement Test");
            }
            if (scheduleRepository.existsByEnrollmentRequestId(enrollmentRequest.getId())
                    || scheduleRepository.existsByEnrollmentRequestIdAndStatusNot(enrollmentRequest.getId(), PlacementScheduleStatus.CANCELLED)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu đã có lịch Placement Test");
            }
            Program requestedProgram = enrollmentRequest.getRequestedProgram();
            if (requestedProgram != null && request.getProgramId() != null
                    && !requestedProgram.getId().equals(request.getProgramId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Môn kiểm tra phải trùng với môn ghi trên đơn");
            }
            program = requirePlacementProgram(requestedProgram == null ? request.getProgramId() : requestedProgram.getId());
            if (requestedProgram == null) {
                enrollmentRequest.setRequestedProgram(program);
            }
        } else {
            program = requirePlacementProgram(request.getProgramId());
        }

        PlacementSchedule schedule = scheduleMapper.toEntity(request);
        schedule.setProgram(program);
        schedule.setSubject(program.getPlacementSubject());
        if (enrollmentRequest != null) {
            schedule.setEnrollmentRequest(enrollmentRequest);
            schedule.setStudentName(enrollmentRequest.getStudent().getFullName());
            schedule.setParent(enrollmentRequest.getRequestedBy());
        } else if (parentUsername != null && !parentUsername.isBlank()) {
            userRepository.findByUsername(parentUsername).ifPresent(schedule::setParent);
        }
        PlacementSchedule saved = scheduleRepository.save(schedule);
        if (notificationService != null) {
            if (saved.getParent() != null) {
                notificationService.sendNotification(
                        saved.getParent(), null,
                        "Đã xếp lịch kiểm tra đầu vào (Placement Test)",
                        "Bé " + saved.getStudentName() + " đã được hẹn lịch đánh giá năng khiếu đầu vào vào lúc " + saved.getTestDate() + ".",
                        "PLACEMENT_SCHEDULED", saved.getId(), "/pages/course-enrollment.html");
            }
            notificationService.sendToRoles(
                    List.of(com.talent.management.shared.enums.Role.TEACHER, com.talent.management.shared.enums.Role.STAFF), null,
                    "Lịch đánh giá đầu vào mới",
                    "Học viên " + saved.getStudentName() + " có lịch kiểm tra năng khiếu vào lúc " + saved.getTestDate() + ".",
                    "PLACEMENT_SCHEDULED", saved.getId(), "/pages/placement-test.html");
        }
        return scheduleMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PlacementScheduleResponse cancelSchedule(Long scheduleId, String cancelReason, String currentUsername) {
        if (cancelReason == null || cancelReason.trim().isEmpty() || cancelReason.length() > 500) {
            throw new BusinessException("Vui lòng nhập lý do hủy đơn Placement Test!");
        }
        String trimmedReason = cancelReason.trim();

        PlacementSchedule schedule = scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new BusinessException("Lịch thi xếp lớp không tồn tại với ID: " + scheduleId));

        if (schedule.getStatus() == PlacementScheduleStatus.CANCELLED) {
            throw new BusinessException("Đơn Placement Test này đã được hủy trước đó!");
        }
        if (schedule.getStatus() == PlacementScheduleStatus.COMPLETED) {
            throw new BusinessException("Không thể hủy đơn Placement Test đã hoàn thành chấm điểm!");
        }

        if (currentUsername != null && !currentUsername.isBlank()) {
            var userOpt = userRepository.findByUsername(currentUsername);
            if (userOpt.isPresent() && userOpt.get().getRole() == com.talent.management.shared.enums.Role.TEACHER) {
                var teacher = userOpt.get();
                String teacherSubject = teacher.getSubject();
                String scheduleSubject = schedule.getSubject() != null ? schedule.getSubject() : scheduleMapper.inferSubject(schedule.getTitle());
                if (teacherSubject != null && !teacherSubject.isBlank() && scheduleSubject != null && !scheduleSubject.isBlank()) {
                    String normTeacher = scheduleMapper.normalizeSubject(teacherSubject);
                    String normSchedule = scheduleMapper.normalizeSubject(scheduleSubject);
                    if (!normTeacher.equalsIgnoreCase(normSchedule)) {
                        throw new BusinessException("Bạn là giáo viên bộ môn " + teacherSubject + ", không có quyền hủy lịch thi thuộc bộ môn " + scheduleSubject + "!");
                    }
                }
            }
        }

        schedule.setStatus(PlacementScheduleStatus.CANCELLED);
        schedule.setCancelReason(trimmedReason);
        schedule.setCancelledAt(LocalDateTime.now());
        // Nếu gắn với EnrollmentRequest đang chờ Placement Test, cho phép tách liên kết để xếp lịch mới hoặc hủy đơn
        EnrollmentRequest enrollmentRequest = schedule.getEnrollmentRequest();
        if (enrollmentRequest != null && enrollmentRequest.getStatus() == EnrollmentRequestStatus.WAITING_PLACEMENT) {
            schedule.setEnrollmentRequest(null);
        }

        PlacementSchedule saved = scheduleRepository.save(schedule);
        if (notificationService != null && saved.getParent() != null) {
            notificationService.sendNotification(
                    saved.getParent(), null,
                    "Lịch kiểm tra đầu vào (Placement Test) đã hủy",
                    "Lịch đánh giá năng khiếu đầu vào của bé " + saved.getStudentName() + " (#PT-" + saved.getId() + ") đã được hủy. Lý do: " + trimmedReason,
                    "PLACEMENT_CANCELLED", saved.getId(), "/pages/course-enrollment.html");
        }
        return scheduleMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PlacementRecommendationResponse getLatestRecommendation(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new BusinessException("Học viên không tồn tại với ID: " + studentId);
        }

        var schedule = scheduleRepository.findFirstByEnrollmentRequestStudentIdOrderByTestDateDescIdDesc(studentId);
        if (schedule.isPresent()) {
            return toRecommendationResponse(schedule.get(), studentId);
        }

        return placementTestRepository.findFirstByStudentIdOrderByTestDateDescCreatedAtDesc(studentId)
                .map(this::toRecommendationResponse)
                .orElseGet(() -> PlacementRecommendationResponse.notAvailable(studentId));
    }

    @Override
    @Transactional(readOnly = true)
    public PlacementRecommendationResponse getLatestRecommendation(Long studentId, Long programId) {
        if (programId == null) {
            return getLatestRecommendation(studentId);
        }
        if (!studentRepository.existsById(studentId)) {
            throw new BusinessException("Học viên không tồn tại với ID: " + studentId);
        }
        return scheduleRepository
                .findFirstByEnrollmentRequestStudentIdAndProgramIdAndStatusAndRecommendedCourseProgramIdOrderByTestDateDescIdDesc(
                        studentId, programId, PlacementScheduleStatus.COMPLETED, programId)
                .map(schedule -> toRecommendationResponse(schedule, studentId))
                .orElseGet(() -> placementTestRepository
                        .findFirstByStudentIdAndProgramIdAndRecommendedCourseProgramIdOrderByTestDateDescCreatedAtDesc(
                                studentId, programId, programId)
                        .map(this::toRecommendationResponse)
                        .orElseGet(() -> PlacementRecommendationResponse.notAvailable(studentId)));
    }

    private PlacementRecommendationResponse toRecommendationResponse(PlacementSchedule schedule, Long studentId) {
        if (schedule.getStatus() != PlacementScheduleStatus.COMPLETED) {
            return PlacementRecommendationResponse.notAvailable(studentId);
        }
        Course course = schedule.getRecommendedCourse();
        return new PlacementRecommendationResponse(
                studentId,
                "COMPLETED",
                course != null,
                schedule.getScore() == null ? null : schedule.getScore() / 10.0,
                schedule.getRecommendedLevel() == null ? null : schedule.getRecommendedLevel().name(),
                course == null ? null : course.getId(),
                course == null ? null : course.getName(),
                schedule.getTeacherNote(),
                schedule.getEvaluatedAt()
        );
    }

    private PlacementRecommendationResponse toRecommendationResponse(PlacementTest placementTest) {
        Course course = placementTest.getRecommendedCourse();
        return new PlacementRecommendationResponse(
                placementTest.getStudent().getId(),
                "COMPLETED",
                course != null,
                placementTest.getTotalScore(),
                placementTest.getRecommendedLevel(),
                course == null ? null : course.getId(),
                course == null ? null : course.getName(),
                placementTest.getTeacherNotes(),
                placementTest.getCreatedAt()
        );
    }
}
