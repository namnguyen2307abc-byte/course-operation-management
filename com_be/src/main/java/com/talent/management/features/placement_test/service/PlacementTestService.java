package com.talent.management.features.placement_test.service;

import com.talent.management.features.placement_test.dto.request.CreatePlacementScheduleRequest;
import com.talent.management.features.placement_test.dto.request.PlacementAssessmentRequest;
import com.talent.management.features.placement_test.dto.response.PlacementScheduleResponse;

import java.util.List;

public interface PlacementTestService {

    List<PlacementScheduleResponse> getAllSchedules();

    List<PlacementScheduleResponse> getSchedulesForParent(String parentEmail);

    PlacementScheduleResponse getScheduleById(Long id);

    PlacementScheduleResponse saveAssessment(Long scheduleId, PlacementAssessmentRequest request, String teacherUsername);

    PlacementScheduleResponse createSchedule(CreatePlacementScheduleRequest request, String parentUsername);
}
