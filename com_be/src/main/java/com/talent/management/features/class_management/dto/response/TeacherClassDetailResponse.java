package com.talent.management.features.class_management.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherClassDetailResponse {
    private TeacherClassSummaryResponse classOverview;
    private List<TeacherClassStudentItem> students;
    private List<TeacherClassLessonItem> lessons;
}
