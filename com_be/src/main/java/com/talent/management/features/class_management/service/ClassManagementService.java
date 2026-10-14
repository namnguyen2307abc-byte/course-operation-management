package com.talent.management.features.class_management.service;

import com.talent.management.features.branch_facility_enrollment.repository.BranchRepository;
import com.talent.management.features.class_management.dto.ClassUpsertRequest;
import com.talent.management.features.class_management.dto.ManagedClassResponse;
import com.talent.management.features.class_management.repository.ManagedClassEnrollmentRepository;
import com.talent.management.features.class_management.repository.ManagedClassLessonRepository;
import com.talent.management.features.class_management.repository.ManagedClassRepository;
import com.talent.management.features.course_enrollment.repository.CourseRepository;
import com.talent.management.shared.entity.Branch;
import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.enums.ClassStatus;
import com.talent.management.shared.enums.ClassType;
import com.talent.management.shared.enums.EnrollmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ClassManagementService {
    private final ManagedClassRepository classRepository;
    private final ManagedClassEnrollmentRepository enrollmentRepository;
    private final ManagedClassLessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final BranchRepository branchRepository;

    @Transactional(readOnly = true)
    public List<ManagedClassResponse> search(String keyword, Long courseId, Long branchId, String status) {
        String term = keyword == null ? "" : keyword.trim();
        return classRepository.search(term, courseId, branchId, parseStatus(status)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ManagedClassResponse get(Long id) {
        return toResponse(findClass(id));
    }

    @Transactional
    public ManagedClassResponse create(ClassUpsertRequest request) {
        String code = normalizeCode(request.classCode());
        validateDates(request);
        ClassStatus status = requiredStatus(request.status());
        if (classRepository.existsByClassCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã lớp đã tồn tại");
        }
        ClassEntity entity = ClassEntity.builder()
                .course(findCourse(request.courseId()))
                .branch(findBranch(request.branchId()))
                .classCode(code)
                .className(request.className().trim())
                .classType(ClassType.GROUP)
                .maxStudents(request.maxStudents())
                .currentStudents(0)
                .status(status)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();
        try {
            return toResponse(classRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã lớp đã tồn tại hoặc dữ liệu liên kết không hợp lệ", ex);
        }
    }

    @Transactional
    public ManagedClassResponse update(Long id, ClassUpsertRequest request) {
        ClassEntity entity = findClass(id);
        String code = normalizeCode(request.classCode());
        validateDates(request);
        ClassStatus status = requiredStatus(request.status());
        if (classRepository.existsByClassCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã lớp đã tồn tại");
        }
        boolean courseOrBranchChanged = !Objects.equals(entity.getCourse().getId(), request.courseId())
                || !Objects.equals(entity.getBranch().getId(), request.branchId());
        if (courseOrBranchChanged && (entity.getCurrentStudents() != null && entity.getCurrentStudents() > 0
                || enrollmentRepository.existsByClassEntityId(id)
                || lessonRepository.existsByClassEntityId(id))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không thể đổi khóa học hoặc cơ sở khi lớp đã có ghi danh hoặc buổi học");
        }
        long occupied = (entity.getCurrentStudents() == null ? 0 : entity.getCurrentStudents())
                + enrollmentRepository.countByClassEntityIdAndStatus(id, EnrollmentStatus.PENDING_PAYMENT);
        if (request.maxStudents() < occupied) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Sĩ số tối đa không được nhỏ hơn số học viên đã ghi danh và đang chờ thanh toán");
        }
        entity.setCourse(findCourse(request.courseId()));
        entity.setBranch(findBranch(request.branchId()));
        entity.setClassCode(code);
        entity.setClassName(request.className().trim());
        entity.setMaxStudents(request.maxStudents());
        entity.setStatus(status);
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        try {
            return toResponse(classRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã lớp đã tồn tại hoặc dữ liệu liên kết không hợp lệ", ex);
        }
    }

    private ManagedClassResponse toResponse(ClassEntity entity) {
        long pending = entity.getId() == null ? 0
                : enrollmentRepository.countByClassEntityIdAndStatus(entity.getId(), EnrollmentStatus.PENDING_PAYMENT);
        int current = entity.getCurrentStudents() == null ? 0 : entity.getCurrentStudents();
        return new ManagedClassResponse(entity.getId(), entity.getCourse().getId(),
                entity.getCourse().getCode(), entity.getCourse().getName(),
                entity.getBranch().getId(), entity.getBranch().getName(),
                entity.getClassCode(), entity.getClassName(), entity.getClassType(),
                entity.getMaxStudents(), current, pending,
                Math.max((long) entity.getMaxStudents() - current - pending, 0),
                entity.getStatus(), entity.getStartDate(), entity.getEndDate());
    }

    private ClassEntity findClass(Long id) {
        return classRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp"));
    }

    private Course findCourse(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khóa học"));
    }

    private Branch findBranch(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy cơ sở"));
    }

    private String normalizeCode(String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9][A-Z0-9_-]*") || normalized.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mã lớp chỉ gồm chữ không dấu, số, dấu gạch ngang hoặc gạch dưới (tối đa 50 ký tự)");
        }
        return normalized;
    }

    private void validateDates(ClassUpsertRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ngày kết thúc dự kiến phải từ ngày bắt đầu trở đi");
        }
    }

    private ClassStatus requiredStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái lớp không được để trống");
        }
        return parseStatus(value);
    }

    private ClassStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ClassStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái lớp không hợp lệ");
        }
    }
}
