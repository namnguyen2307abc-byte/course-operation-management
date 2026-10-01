package com.talent.management.features.placement_test.service.impl;

import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.placement_test.dto.request.CreatePlacementScheduleRequest;
import com.talent.management.features.placement_test.dto.request.PlacementAssessmentRequest;
import com.talent.management.features.placement_test.dto.response.PlacementScheduleResponse;
import com.talent.management.features.placement_test.dto.response.PlacementRecommendationResponse;
import com.talent.management.features.placement_test.entity.PlacementSchedule;
import com.talent.management.features.placement_test.entity.PlacementScheduleStatus;
import com.talent.management.features.placement_test.mapper.PlacementScheduleMapper;
import com.talent.management.features.placement_test.repository.PlacementScheduleRepository;
import com.talent.management.features.placement_test.repository.PlacementTestRepository;
import com.talent.management.features.placement_test.service.PlacementTestService;
import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.PlacementTest;
import com.talent.management.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlacementTestServiceImpl implements PlacementTestService {

    private final PlacementScheduleRepository scheduleRepository;
    private final PlacementTestRepository placementTestRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final PlacementScheduleMapper scheduleMapper;

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
        PlacementSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException("Lịch thi xếp lớp không tồn tại với ID: " + scheduleId));

        if (teacherUsername != null && !teacherUsername.isBlank()) {
            var teacherOpt = userRepository.findByUsername(teacherUsername);
            if (teacherOpt.isPresent()) {
                var teacher = teacherOpt.get();
                if (teacher.getRole() == com.talent.management.shared.enums.Role.TEACHER) {
                    String teacherSubject = teacher.getSubject();
                    String scheduleSubject = schedule.getSubject() != null ? schedule.getSubject() : scheduleMapper.inferSubject(schedule.getTitle());
                    if (teacherSubject != null && !teacherSubject.isBlank() && scheduleSubject != null && !scheduleSubject.isBlank()) {
                        String normTeacher = scheduleMapper.normalizeSubject(teacherSubject);
                        String normSchedule = scheduleMapper.normalizeSubject(scheduleSubject);
                        if (!normTeacher.equalsIgnoreCase(normSchedule)) {
                            throw new BusinessException("Bạn là giáo viên bộ môn " + teacherSubject + ", không có quyền chấm bài thi xếp lớp thuộc bộ môn " + scheduleSubject + "!");
                        }
                    }
                }
                schedule.setEvaluatedBy(teacher);
            }
        }

        schedule.setScore(request.getScore());
        schedule.setRecommendedLevel(request.getRecommendedLevel());
        schedule.setTeacherNote(request.getTeacherNote());
        schedule.setAudioUrl(request.getAudioUrl());
        schedule.setVideoUrl(request.getVideoUrl());
        schedule.setImageUrl(request.getImageUrl());
        schedule.setRecordUrl(request.getRecordUrl());
        schedule.setEvaluatedAt(LocalDateTime.now());
        schedule.setStatus(PlacementScheduleStatus.COMPLETED);

        PlacementSchedule saved = scheduleRepository.save(schedule);
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

        PlacementSchedule schedule = scheduleMapper.toEntity(request);
        if (parentUsername != null && !parentUsername.isBlank()) {
            userRepository.findByUsername(parentUsername).ifPresent(schedule::setParent);
        }
        PlacementSchedule saved = scheduleRepository.save(schedule);
        return scheduleMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PlacementRecommendationResponse getLatestRecommendation(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new BusinessException("Học viên không tồn tại với ID: " + studentId);
        }

        return placementTestRepository.findFirstByStudentIdOrderByTestDateDescCreatedAtDesc(studentId)
                .map(this::toRecommendationResponse)
                .orElseGet(() -> PlacementRecommendationResponse.notAvailable(studentId));
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
