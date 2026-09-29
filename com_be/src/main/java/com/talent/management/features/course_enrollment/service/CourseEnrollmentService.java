package com.talent.management.features.course_enrollment.service;

import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.features.course_enrollment.dto.request.EnrollmentDecisionRequest;
import com.talent.management.features.course_enrollment.dto.request.EnrollmentRequestCreateRequest;
import com.talent.management.features.course_enrollment.dto.response.ChildResponse;
import com.talent.management.features.course_enrollment.dto.response.ClassResponse;
import com.talent.management.features.course_enrollment.dto.response.ClassRecommendationResponse;
import com.talent.management.features.course_enrollment.dto.response.CourseResponse;
import com.talent.management.features.course_enrollment.dto.response.EnrollmentRequestResponse;
import com.talent.management.features.course_enrollment.entity.EnrollmentRequest;
import com.talent.management.features.course_enrollment.enums.EnrollmentDecision;
import com.talent.management.features.course_enrollment.enums.EnrollmentRequestStatus;
import com.talent.management.features.course_enrollment.mapper.CourseEnrollmentMapper;
import com.talent.management.features.course_enrollment.repository.ClassRepository;
import com.talent.management.features.course_enrollment.repository.CourseRepository;
import com.talent.management.features.course_enrollment.repository.EnrollmentRepository;
import com.talent.management.features.course_enrollment.repository.EnrollmentRequestRepository;
import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.Enrollment;
import com.talent.management.shared.entity.Student;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.ClassStatus;
import com.talent.management.shared.enums.EnrollmentStatus;
import com.talent.management.shared.service.CurrentUserService;
import com.talent.management.features.placement_test.dto.response.PlacementRecommendationResponse;
import com.talent.management.features.placement_test.service.PlacementTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CourseEnrollmentService {

    private static final EnumSet<EnrollmentStatus> ACTIVE_ENROLLMENT_STATUSES =
            EnumSet.of(EnrollmentStatus.PENDING_PAYMENT, EnrollmentStatus.ENROLLED);
    private static final EnumSet<EnrollmentRequestStatus> ACTIONABLE_REQUEST_STATUSES =
            EnumSet.of(
                    EnrollmentRequestStatus.PENDING,
                    EnrollmentRequestStatus.WAITING_PLACEMENT,
                    EnrollmentRequestStatus.READY_FOR_ASSIGNMENT
            );
    private static final EnumSet<EnrollmentRequestStatus> OPEN_REQUEST_STATUSES =
            EnumSet.of(
                    EnrollmentRequestStatus.PENDING,
                    EnrollmentRequestStatus.WAITING_PLACEMENT,
                    EnrollmentRequestStatus.READY_FOR_ASSIGNMENT,
                    EnrollmentRequestStatus.PENDING_PAYMENT
            );

    private final CurrentUserService currentUserService;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final ClassRepository classRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentRequestRepository requestRepository;
    private final CourseEnrollmentMapper mapper;
    private final PlacementTestService placementTestService;

    @Transactional(readOnly = true)
    public List<ChildResponse> getMyChildren() {
        User parent = currentUserService.getCurrentUser();
        return studentRepository.findByParentId(parent.getId()).stream()
                .map(mapper::toChildResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> getCourses() {
        return courseRepository.findAllByOrderByProgramNameAscNameAsc().stream()
                .map(mapper::toCourseResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClassResponse> getOpenClasses(Long courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khóa học");
        }

        return classRepository.findByCourseIdAndStatusOrderByStartDateAsc(courseId, ClassStatus.OPEN).stream()
                .filter(this::hasAvailableSeat)
                .map(mapper::toClassResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClassRecommendationResponse getClassRecommendations(Long childId) {
        User parent = currentUserService.getCurrentUser();
        Student child = studentRepository.findByIdAndParentId(childId, parent.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy học viên thuộc tài khoản của bạn"
                ));

        PlacementRecommendationResponse placement = placementTestService.getLatestRecommendation(childId);
        List<ClassResponse> openClasses = classRepository
                .findByStatusOrderByStartDateAsc(ClassStatus.OPEN)
                .stream()
                .filter(this::hasAvailableSeat)
                .map(mapper::toClassResponse)
                .toList();

        List<ClassResponse> recommendedClasses = placement.recommendationAvailable()
                ? openClasses.stream()
                        .filter(item -> Objects.equals(item.courseId(), placement.recommendedCourseId()))
                        .toList()
                : List.of();

        return new ClassRecommendationResponse(
                child.getId(),
                child.getFullName(),
                placement.placementStatus(),
                placement.recommendationAvailable(),
                placement.score(),
                placement.recommendedLevel(),
                placement.recommendedCourseId(),
                placement.recommendedCourseName(),
                placement.teacherNote(),
                recommendedClasses,
                openClasses
        );
    }

    @Transactional
    public EnrollmentRequestResponse submitRequest(EnrollmentRequestCreateRequest input) {
        User parent = currentUserService.getCurrentUser();
        Student child = studentRepository.findByIdAndParentId(input.childId(), parent.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy học viên thuộc tài khoản của bạn"
                ));
        ensureNoOpenRequest(child.getId());

        Course preferredCourse = null;
        EnrollmentRequestStatus status;
        if (input.placementRequested()) {
            PlacementRecommendationResponse placement = placementTestService.getLatestRecommendation(child.getId());
            if (placement.recommendationAvailable() && placement.recommendedCourseId() != null) {
                preferredCourse = getCourseOrThrow(placement.recommendedCourseId());
                status = EnrollmentRequestStatus.READY_FOR_ASSIGNMENT;
            } else {
                status = EnrollmentRequestStatus.WAITING_PLACEMENT;
            }
        } else {
            if (input.preferredCourseId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Vui lòng chọn khóa học mong muốn nếu không đăng ký Placement Test"
                );
            }
            preferredCourse = getCourseOrThrow(input.preferredCourseId());
            status = EnrollmentRequestStatus.READY_FOR_ASSIGNMENT;
        }

        EnrollmentRequest request = EnrollmentRequest.builder()
                .student(child)
                .preferredCourse(preferredCourse)
                .placementRequested(input.placementRequested())
                .requestedBy(parent)
                .status(status)
                .note(normalize(input.note()))
                .build();

        return mapper.toRequestResponse(requestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public List<EnrollmentRequestResponse> getMyRequests() {
        User parent = currentUserService.getCurrentUser();
        return requestRepository.findByRequestedByIdOrderByCreatedAtDesc(parent.getId()).stream()
                .map(mapper::toRequestResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EnrollmentRequestResponse> getPendingRequests() {
        return requestRepository.findByStatusInOrderByCreatedAtAsc(ACTIONABLE_REQUEST_STATUSES).stream()
                .map(mapper::toRequestResponse)
                .toList();
    }

    @Transactional
    public EnrollmentRequestResponse refreshPlacement(Long requestId) {
        EnrollmentRequest request = getRequestOrThrow(requestId);
        if (!request.isPlacementRequested()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Yêu cầu này không đăng ký Placement Test");
        }
        if (request.getStatus() != EnrollmentRequestStatus.WAITING_PLACEMENT) {
            return mapper.toRequestResponse(request);
        }

        syncPlacementResult(request);
        return mapper.toRequestResponse(requestRepository.save(request));
    }

    @Transactional
    public EnrollmentRequestResponse decide(Long requestId, EnrollmentDecisionRequest input) {
        User reviewer = currentUserService.getCurrentUser();
        EnrollmentRequest request = getRequestOrThrow(requestId);

        if (!ACTIONABLE_REQUEST_STATUSES.contains(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu này đã được xử lý");
        }

        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNote(normalize(input.reviewNote()));

        if (input.decision() == EnrollmentDecision.REJECT) {
            request.setStatus(EnrollmentRequestStatus.REJECTED);
            return mapper.toRequestResponse(requestRepository.save(request));
        }

        if (request.isPlacementRequested() && request.getStatus() == EnrollmentRequestStatus.WAITING_PLACEMENT) {
            syncPlacementResult(request);
        }
        if (request.getStatus() == EnrollmentRequestStatus.WAITING_PLACEMENT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Học viên chưa có kết quả Placement Test để xếp lớp"
            );
        }

        Long assignedClassId = input.classId() != null
                ? input.classId()
                : request.getClassEntity() == null ? null : request.getClassEntity().getId();
        if (assignedClassId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn lớp cho học viên");
        }

        ClassEntity classEntity = getClassOrThrow(assignedClassId);
        ensureClassCanReceiveRequest(classEntity);
        if (request.getPreferredCourse() != null
                && !Objects.equals(classEntity.getCourse().getId(), request.getPreferredCourse().getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Lớp được chọn không thuộc khóa học mong muốn hoặc khóa học được đề xuất"
            );
        }
        if (hasActiveEnrollment(request.getStudent().getId(), classEntity.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Học viên đã được ghi danh vào lớp này");
        }

        Enrollment enrollment = Enrollment.builder()
                .student(request.getStudent())
                .classEntity(classEntity)
                .registeredByUser(reviewer)
                .status(EnrollmentStatus.PENDING_PAYMENT)
                .notes("Chờ thanh toán từ yêu cầu đăng ký lớp #" + request.getId())
                .build();
        enrollmentRepository.save(enrollment);

        request.setClassEntity(classEntity);
        request.setEnrollment(enrollment);
        request.setStatus(EnrollmentRequestStatus.PENDING_PAYMENT);
        return mapper.toRequestResponse(requestRepository.save(request));
    }

    private EnrollmentRequest getRequestOrThrow(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đăng ký"));
    }

    private Course getCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khóa học"));
    }

    private void syncPlacementResult(EnrollmentRequest request) {
        PlacementRecommendationResponse placement = placementTestService
                .getLatestRecommendation(request.getStudent().getId());
        if (!placement.recommendationAvailable() || placement.recommendedCourseId() == null) {
            return;
        }

        request.setPreferredCourse(getCourseOrThrow(placement.recommendedCourseId()));
        request.setStatus(EnrollmentRequestStatus.READY_FOR_ASSIGNMENT);
    }

    private ClassEntity getClassOrThrow(Long classId) {
        return classRepository.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp học"));
    }

    private void ensureClassCanReceiveRequest(ClassEntity classEntity) {
        if (classEntity.getStatus() != ClassStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lớp học hiện không mở đăng ký");
        }
        if (!hasAvailableSeat(classEntity)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lớp học đã đủ sĩ số");
        }
    }

    private boolean hasAvailableSeat(ClassEntity classEntity) {
        int currentStudents = classEntity.getCurrentStudents() == null ? 0 : classEntity.getCurrentStudents();
        int maxStudents = classEntity.getMaxStudents() == null ? 0 : classEntity.getMaxStudents();
        long pendingPayment = classEntity.getId() == null
                ? 0
                : enrollmentRepository.countByClassEntityIdAndStatus(
                        classEntity.getId(),
                        EnrollmentStatus.PENDING_PAYMENT
                );
        return currentStudents + pendingPayment < maxStudents;
    }

    private void ensureNoOpenRequest(Long studentId) {
        if (requestRepository.existsByStudentIdAndStatusIn(studentId, OPEN_REQUEST_STATUSES)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Học viên đã có một yêu cầu đang được phòng đào tạo xử lý"
            );
        }
    }

    private boolean hasActiveEnrollment(Long studentId, Long classId) {
        return enrollmentRepository.existsByStudentIdAndClassEntityIdAndStatusIn(
                studentId,
                classId,
                ACTIVE_ENROLLMENT_STATUSES
        );
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
