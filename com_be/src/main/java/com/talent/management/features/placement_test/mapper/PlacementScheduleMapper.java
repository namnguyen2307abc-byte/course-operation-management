package com.talent.management.features.placement_test.mapper;

import com.talent.management.features.placement_test.dto.request.CreatePlacementScheduleRequest;
import com.talent.management.features.placement_test.dto.response.PlacementScheduleResponse;
import com.talent.management.features.placement_test.entity.PlacementSchedule;
import com.talent.management.features.placement_test.entity.PlacementScheduleStatus;
import org.springframework.stereotype.Component;

@Component
public class PlacementScheduleMapper {

    public PlacementScheduleResponse toResponse(PlacementSchedule schedule) {
        if (schedule == null) {
            return null;
        }

        return PlacementScheduleResponse.builder()
                .id(schedule.getId())
                .studentName(schedule.getStudentName())
                .title(schedule.getTitle())
                .roomName(schedule.getRoomName())
                .branch(schedule.getBranch())
                .testDate(schedule.getTestDate())
                .note(schedule.getNote())
                .status(schedule.getStatus())
                .score(schedule.getScore())
                .recommendedLevel(schedule.getRecommendedLevel())
                .teacherNote(schedule.getTeacherNote())
                .audioUrl(schedule.getAudioUrl())
                .videoUrl(schedule.getVideoUrl())
                .imageUrl(schedule.getImageUrl())
                .recordUrl(schedule.getRecordUrl())
                .evaluatedById(schedule.getEvaluatedBy() != null ? schedule.getEvaluatedBy().getId() : null)
                .evaluatedByName(schedule.getEvaluatedBy() != null ? schedule.getEvaluatedBy().getFullName() : null)
                .evaluatedAt(schedule.getEvaluatedAt())
                .parentId(schedule.getParent() != null ? schedule.getParent().getId() : null)
                .parentName(schedule.getParent() != null ? schedule.getParent().getFullName() : null)
                .parentEmail(schedule.getParent() != null ? schedule.getParent().getEmail() : null)
                .createdAt(schedule.getCreatedAt())
                .build();
    }

    public PlacementSchedule toEntity(CreatePlacementScheduleRequest request) {
        if (request == null) {
            return null;
        }

        return PlacementSchedule.builder()
                .studentName(request.getStudentName())
                .title(request.getTitle())
                .roomName(request.getRoomName())
                .branch(request.getBranch())
                .testDate(request.getTestDate())
                .note(request.getNote())
                .status(PlacementScheduleStatus.SCHEDULED)
                .build();
    }
}
