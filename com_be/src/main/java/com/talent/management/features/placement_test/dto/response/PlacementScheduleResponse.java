package com.talent.management.features.placement_test.dto.response;

import com.talent.management.features.placement_test.entity.PlacementLevel;
import com.talent.management.features.placement_test.entity.PlacementScheduleStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlacementScheduleResponse {

    private Long id;
    private String studentName;
    private String title;
    private String roomName;
    private String branch;
    private LocalDateTime testDate;
    private String note;
    private PlacementScheduleStatus status;
    private Integer score;
    private PlacementLevel recommendedLevel;
    private String teacherNote;
    private String audioUrl;
    private String videoUrl;
    private String imageUrl;
    private String recordUrl;
    private Long evaluatedById;
    private String evaluatedByName;
    private LocalDateTime evaluatedAt;
    private Long parentId;
    private String parentName;
    private String parentEmail;
    private LocalDateTime createdAt;
}
