package com.talent.management.features.course_enrollment.service;

import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.features.course_enrollment.dto.request.EnrollmentDecisionRequest;
import com.talent.management.features.course_enrollment.dto.request.EnrollmentRequestCreateRequest;
import com.talent.management.features.course_enrollment.dto.response.ChildResponse;
import com.talent.management.features.course_enrollment.dto.response.ClassResponse;
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
import com.talent.management.shared.entity.Enrollment;
import com.talent.management.shared.entity.Student;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.ClassStatus;
import com.talent.management.shared.enums.EnrollmentStatus;
import com.talent.management.shared.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseEnrollmentService {

    private static final EnumSet<EnrollmentStatus> ACTIVE_ENROLLMENT_STATUSES =
            EnumSet.of(EnrollmentStatus.PENDING_PAYMENT, EnrollmentStatus.ENROLLED);

    private final CurrentUserService currentUserService;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final ClassRepository classRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentRequestRepository requestRepository;
    private final CourseEnrollmentMapper mapper;

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

    @Transactional
    public EnrollmentRequestResponse submitRequest(EnrollmentRequestCreateRequest input) {
        User parent = currentUserService.getCurrentUser();
        Student child = studentRepository.findByIdAndParentId(input.childId(), parent.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy học viên thuộc tài khoản của bạn"
                ));
        ClassEntity classEntity = getClassOrThrow(input.classId());

        ensureClassCanReceiveRequest(classEntity);
        ensureNoDuplicate(child.getId(), classEntity.getId());

        EnrollmentRequest request = EnrollmentRequest.builder()
                .student(child)
                .classEntity(classEntity)
                .requestedBy(parent)
                .status(EnrollmentRequestStatus.PENDING)
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
        return requestRepository.findByStatusOrderByCreatedAtAsc(EnrollmentRequestStatus.PENDING).stream()
                .map(mapper::toRequestResponse)
                .toList();
    }

    @Transactional
    public EnrollmentRequestResponse decide(Long requestId, EnrollmentDecisionRequest input) {
        User reviewer = currentUserService.getCurrentUser();
        EnrollmentRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đăng ký"));

        if (request.getStatus() != EnrollmentRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu này đã được xử lý");
        }

        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNote(normalize(input.reviewNote()));

        if (input.decision() == EnrollmentDecision.REJECT) {
            request.setStatus(EnrollmentRequestStatus.REJECTED);
            return mapper.toRequestResponse(requestRepository.save(request));
        }

        ClassEntity classEntity = request.getClassEntity();
        ensureClassCanReceiveRequest(classEntity);
        if (hasActiveEnrollment(request.getStudent().getId(), classEntity.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Học viên đã được ghi danh vào lớp này");
        }

        Enrollment enrollment = Enrollment.builder()
                .student(request.getStudent())
                .classEntity(classEntity)
                .registeredByUser(request.getRequestedBy())
                .status(EnrollmentStatus.ENROLLED)
                .notes("Tạo từ yêu cầu đăng ký lớp #" + request.getId())
                .build();
        enrollmentRepository.save(enrollment);

        int currentStudents = classEntity.getCurrentStudents() == null ? 0 : classEntity.getCurrentStudents();
        classEntity.setCurrentStudents(currentStudents + 1);
        classRepository.save(classEntity);

        request.setEnrollment(enrollment);
        request.setStatus(EnrollmentRequestStatus.APPROVED);
        return mapper.toRequestResponse(requestRepository.save(request));
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
        return currentStudents < maxStudents;
    }

    private void ensureNoDuplicate(Long studentId, Long classId) {
        if (requestRepository.existsByStudentIdAndClassEntityIdAndStatus(
                studentId,
                classId,
                EnrollmentRequestStatus.PENDING
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đã có yêu cầu đang chờ duyệt cho lớp này");
        }
        if (hasActiveEnrollment(studentId, classId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Học viên đã được ghi danh vào lớp này");
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
