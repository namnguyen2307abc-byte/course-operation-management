package com.talent.management.features.placement_test.service.impl;

import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.placement_test.dto.request.CreatePlacementScheduleRequest;
import com.talent.management.features.placement_test.dto.request.PlacementAssessmentRequest;
import com.talent.management.features.placement_test.dto.response.PlacementScheduleResponse;
import com.talent.management.features.placement_test.entity.PlacementSchedule;
import com.talent.management.features.placement_test.entity.PlacementScheduleStatus;
import com.talent.management.features.placement_test.mapper.PlacementScheduleMapper;
import com.talent.management.features.placement_test.repository.PlacementScheduleRepository;
import com.talent.management.features.placement_test.service.PlacementTestService;
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
    private final UserRepository userRepository;
    private final PlacementScheduleMapper scheduleMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PlacementScheduleResponse> getAllSchedules() {
        return scheduleRepository.findAllByOrderByTestDateDesc()
                .stream()
                .map(scheduleMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlacementScheduleResponse> getSchedulesForParent(String parentEmail) {
        if (parentEmail == null || parentEmail.isBlank()) {
            return getAllSchedules();
        }
        return scheduleRepository.findByParentEmailOrderByTestDateAsc(parentEmail.trim().toLowerCase())
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
            userRepository.findByUsername(teacherUsername).ifPresent(schedule::setEvaluatedBy);
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
        PlacementSchedule schedule = scheduleMapper.toEntity(request);
        if (parentUsername != null && !parentUsername.isBlank()) {
            userRepository.findByUsername(parentUsername).ifPresent(schedule::setParent);
        }
        PlacementSchedule saved = scheduleRepository.save(schedule);
        return scheduleMapper.toResponse(saved);
    }
}
