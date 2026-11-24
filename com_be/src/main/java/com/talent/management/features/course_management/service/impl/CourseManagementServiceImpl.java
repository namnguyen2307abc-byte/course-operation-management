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
        BigDecimal validatedTuitionFee = validateTuitionFee(request.getTuitionFee());

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
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .build();

        Course saved = courseRepository.save(course);
        return toCourseDetailResponse(saved);
    }

    @Override
    @Transactional
    public CourseDetailResponse updateCourse(Long id, UpdateCourseRequest request) {
        Integer validatedTotalSessions = validateTotalSessions(request.getTotalSessions());
        BigDecimal validatedTuitionFee = validateTuitionFee(request.getTuitionFee());

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
            return null;
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
        if (totalSessions.compareTo(BigDecimal.valueOf(1000)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tổng số buổi học không được vượt quá 1000 buổi");
        }
        return totalSessions.intValueExact();
    }

    private BigDecimal validateTuitionFee(BigDecimal tuitionFee) {
        if (tuitionFee == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Học phí không được để trống");
        }
        // 1. Tách riêng ngoại lệ số âm
        if (tuitionFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Học phí không được là số âm");
        }
        // 2. Tách riêng ngoại lệ số thập phân
        if (tuitionFee.stripTrailingZeros().scale() > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Học phí phải là số nguyên, không được nhập số thập phân");
        }
        // 3. Tách riêng ngoại lệ bằng 0
        if (tuitionFee.compareTo(BigDecimal.ZERO) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Học phí phải lớn hơn 0");
        }
        // 4. Kiểm tra giới hạn tối đa theo cột DECIMAL(12,2) trong DB
        if (tuitionFee.compareTo(BigDecimal.valueOf(9_999_999_999L)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Học phí không được vượt quá 9.999.999.999 VNĐ");
        }
        return tuitionFee;
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
                totalEnrollments != null ? totalEnrollments : 0L
        );
    }
}
