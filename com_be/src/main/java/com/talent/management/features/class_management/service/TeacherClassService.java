package com.talent.management.features.class_management.service;

import com.talent.management.features.class_management.dto.response.TeacherClassDetailResponse;
import com.talent.management.features.class_management.dto.response.TeacherClassSummaryResponse;

import java.util.List;

public interface TeacherClassService {
    List<TeacherClassSummaryResponse> getTeacherClasses(Long teacherId);
    TeacherClassDetailResponse getTeacherClassDetail(Long classId);
}
