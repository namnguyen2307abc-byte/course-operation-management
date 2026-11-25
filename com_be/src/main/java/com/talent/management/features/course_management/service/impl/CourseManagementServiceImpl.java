package com.talent.management.features.course_management.service.impl;

import com.talent.management.features.course_management.dto.request.CreateCourseRequest;
import com.talent.management.features.course_management.dto.request.UpdateCourseRequest;
import com.talent.management.features.course_management.dto.response.CourseDetailResponse;
import com.talent.management.features.course_management.dto.response.ProgramOptionResponse;
import com.talent.management.features.course_management.repository.CourseManagementRepository;
import com.talent.management.features.course_management.repository.ProgramRepository;
import com.talent.management.features.course_management.service.CourseManagementService;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.Program;
import com.talent.management.shared.policy.CourseSessionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseManagementServiceImpl implements CourseManagementService {

    private final CourseManagementRepository courseRepository;
    private final ProgramRepository programRepository;

    @Override
    public List<CourseDetailResponse> getAllCourses() {
        return courseRepository.findAllWithProgram().stream()
                .map(this::toCourseDetailResponse)
                .toList();
    }

    @Override
    public CourseDetailResponse getCourseById(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy khóa học với id: " + id));
        return toCourseDetailResponse(course);
    }

    @Override
    public List<ProgramOptionResponse> getAllPrograms() {
        return programRepository.findAllByOrderByNameAsc().stream()
                .map(p -> new ProgramOptionResponse(p.getId(), p.getCode(), p.getName(), p.getDescription()))
                .toList();
    }

    @Override
    @Transactional
    public CourseDetailResponse createCourse(CreateCourseRequest request) {
        Integer validatedTotalSessions = validateTotalSessions(request.getTotalSessions());
        BigDecimal unitPrice = validateUnitPrice(request.getTuitionPerSession());
        BigDecimal validatedTuitionFee = calculateTuition(unitPrice, validatedTotalSessions);

        // Validate code uniqueness
        String trimmedCode = request.getCode().trim();
        if (courseRepository.existsByCode(trimmedCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Mã khóa học '" + trimmedCode + "' đã tồn tại trong hệ thống");
        }

        Program program = programRepository.findById(request.getProgramId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy bộ môn với id: " + request.getProgramId()));

        Course course = Course.builder()
                .program(program)
                .code(trimmedCode)
                .name(request.getName().trim())
                .level(request.getLevel().name())
                .durationWeeks(request.getDurationWeeks())
                .totalSessions(validatedTotalSessions)
                .tuitionFee(validatedTuitionFee)
                .tuitionPerSession(unitPrice)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .build();

        Course saved = courseRepository.save(course);
        return toCourseDetailResponse(saved);
    }

    @Override
    @Transactional
    public CourseDetailResponse updateCourse(Long id, UpdateCourseRequest request) {
        Integer validatedTotalSessions = validateTotalSessions(request.getTotalSessions());
        BigDecimal unitPrice = validateUnitPrice(request.getTuitionPerSession());
        BigDecimal validatedTuitionFee = calculateTuition(unitPrice, validatedTotalSessions);

        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy khóa học với id: " + id));

        // Validate code uniqueness (excluding current record)
        String trimmedCode = request.getCode().trim();
        if (courseRepository.existsByCodeAndIdNot(trimmedCode, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Mã khóa học '" + trimmedCode + "' đã được sử dụng bởi khóa học khác");
        }

        Program program = programRepository.findById(request.getProgramId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy bộ môn với id: " + request.getProgramId()));

        course.setProgram(program);
        course.setCode(trimmedCode);
        course.setName(request.getName().trim());
        course.setLevel(request.getLevel().name());
        course.setDurationWeeks(request.getDurationWeeks());
        course.setTotalSessions(validatedTotalSessions);
        course.setTuitionFee(validatedTuitionFee);
        course.setTuitionPerSession(unitPrice);
        course.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);

        Course saved = courseRepository.save(course);
        return toCourseDetailResponse(saved);
    }

    @Override
    @Transactional
    public void deleteCourse(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy khóa học với id: " + id));

        // Check if the course has any classes
        Long classCount = courseRepository.countClassesByCourseId(id);
        if (classCount != null && classCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không thể xóa khóa học đang có " + classCount + " lớp học. Vui lòng xóa hoặc chuyển các lớp học trước.");
        }

        courseRepository.delete(course);
    }

    private Integer validateTotalSessions(BigDecimal totalSessions) {
        if (totalSessions == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tổng số buổi học không được để trống");
        }
        // 1. Tách riêng ngoại lệ số âm
        if (totalSessions.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tổng số buổi học không được là số âm");
        }
        // 2. Tách riêng ngoại lệ số thập phân
        if (totalSessions.stripTrailingZeros().scale() > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tổng số buổi học phải là số nguyên, không được nhập số thập phân");
        }
        // 3. Tách riêng ngoại lệ bằng 0
        if (totalSessions.compareTo(BigDecimal.ZERO) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tổng số buổi học phải lớn hơn 0");
        }
        // 4. Kiểm tra vượt quá giới hạn tối đa
        if (totalSessions.compareTo(BigDecimal.valueOf(60)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tổng số buổi học không được vượt quá 60 buổi");
        }
        return CourseSessionPolicy.requireStandardSessions(totalSessions.intValueExact());
    }

    private BigDecimal validateUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đơn giá mỗi buổi không được để trống");
        }
        // 1. Tách riêng ngoại lệ số âm
        if (unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đơn giá mỗi buổi không được là số âm");
        }
        // 2. Tách riêng ngoại lệ số thập phân
        if (unitPrice.stripTrailingZeros().scale() > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đơn giá mỗi buổi phải là số nguyên VNĐ, không được nhập số thập phân");
        }
        // 3. Tách riêng ngoại lệ bằng 0
        if (unitPrice.compareTo(BigDecimal.ZERO) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đơn giá mỗi buổi phải lớn hơn 0");
        }
        if (unitPrice.compareTo(BigDecimal.valueOf(50000)) < 0
                || unitPrice.compareTo(BigDecimal.valueOf(1000000)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đơn giá mỗi buổi phải từ 50.000 đến 1.000.000 đồng");
        }
        return unitPrice;
    }

    private BigDecimal calculateTuition(BigDecimal unitPrice, int sessions) {
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(sessions));
        // Collection requires at least 1,000 VND; keep the result within the existing money columns.
        if (total.compareTo(BigDecimal.valueOf(1000)) < 0
                || total.compareTo(new BigDecimal("9999999999")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tổng học phí phải từ 1.000 đến 9.999.999.999 đồng. Vui lòng kiểm tra đơn giá mỗi buổi.");
        }
        return total;
    }

    private CourseDetailResponse toCourseDetailResponse(Course course) {
        Long totalClasses = courseRepository.countClassesByCourseId(course.getId());
        Long totalEnrollments = courseRepository.countEnrollmentsByCourseId(course.getId());

        return new CourseDetailResponse(
                course.getId(),
                course.getProgram().getId(),
                course.getProgram().getCode(),
                course.getProgram().getName(),
                course.getCode(),
                course.getName(),
                course.getLevel(),
                course.getDurationWeeks(),
                course.getTotalSessions(),
                course.getTuitionFee(),
                course.getDescription(),
                totalClasses != null ? totalClasses : 0L,
                totalEnrollments != null ? totalEnrollments : 0L,
                course.getTuitionPerSession()
        );
    }
}
