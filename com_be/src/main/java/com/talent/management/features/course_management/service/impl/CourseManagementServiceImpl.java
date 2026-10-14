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
        // Validate code uniqueness
        if (courseRepository.existsByCode(request.getCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Mã khóa học '" + request.getCode() + "' đã tồn tại trong hệ thống");
        }

        Program program = programRepository.findById(request.getProgramId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy bộ môn với id: " + request.getProgramId()));

        Course course = Course.builder()
                .program(program)
                .code(request.getCode().trim())
                .name(request.getName().trim())
                .level(request.getLevel() != null ? request.getLevel().name() : null)
                .durationWeeks(request.getDurationWeeks())
                .totalSessions(request.getTotalSessions())
                .tuitionFee(request.getTuitionFee())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .build();

        Course saved = courseRepository.save(course);
        return toCourseDetailResponse(saved);
    }

    @Override
    @Transactional
    public CourseDetailResponse updateCourse(Long id, UpdateCourseRequest request) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy khóa học với id: " + id));

        // Validate code uniqueness (excluding current record)
        if (courseRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Mã khóa học '" + request.getCode() + "' đã được sử dụng bởi khóa học khác");
        }

        Program program = programRepository.findById(request.getProgramId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy bộ môn với id: " + request.getProgramId()));

        course.setProgram(program);
        course.setCode(request.getCode().trim());
        course.setName(request.getName().trim());
        course.setLevel(request.getLevel() != null ? request.getLevel().name() : null);
        course.setDurationWeeks(request.getDurationWeeks());
        course.setTotalSessions(request.getTotalSessions());
        course.setTuitionFee(request.getTuitionFee());
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
