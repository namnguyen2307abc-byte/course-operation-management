package com.talent.management.features.placement_test.service;

import com.talent.management.features.placement_test.dto.request.CreatePlacementScheduleRequest;
import com.talent.management.features.placement_test.dto.request.PlacementAssessmentRequest;
import com.talent.management.features.placement_test.dto.response.PlacementScheduleResponse;
import com.talent.management.features.placement_test.dto.response.PlacementRecommendationResponse;
import com.talent.management.features.placement_test.dto.response.PlacementProgramResponse;
import com.talent.management.shared.entity.Program;

import java.util.List;

public interface PlacementTestService {

    List<PlacementScheduleResponse> getAllSchedules();

    List<PlacementScheduleResponse> getAllSchedules(String currentUsername);

    List<PlacementScheduleResponse> getSchedulesForParent(String parentEmail);

    List<PlacementScheduleResponse> getSchedulesForParent(String parentEmail, String currentUsername);

    PlacementScheduleResponse getScheduleById(Long id);

    PlacementScheduleResponse saveAssessment(Long scheduleId, PlacementAssessmentRequest request, String teacherUsername);

    PlacementScheduleResponse createSchedule(CreatePlacementScheduleRequest request, String parentUsername);

    PlacementScheduleResponse cancelSchedule(Long scheduleId, String cancelReason, String currentUsername);

    PlacementRecommendationResponse getLatestRecommendation(Long studentId);

    PlacementRecommendationResponse getLatestRecommendation(Long studentId, Long programId);

    List<PlacementProgramResponse> getPlacementPrograms();

    Program requirePlacementProgram(Long programId);
}
