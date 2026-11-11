package com.talent.management.features.course_management.service;

import com.talent.management.features.course_management.dto.request.CreateCourseRequest;
import com.talent.management.features.course_management.dto.request.UpdateCourseRequest;
import com.talent.management.features.course_management.dto.response.CourseDetailResponse;
import com.talent.management.features.course_management.dto.response.ProgramOptionResponse;

import java.util.List;

public interface CourseManagementService {

    List<CourseDetailResponse> getAllCourses();

    CourseDetailResponse getCourseById(Long id);

    List<ProgramOptionResponse> getAllPrograms();

    CourseDetailResponse createCourse(CreateCourseRequest request);

    CourseDetailResponse updateCourse(Long id, UpdateCourseRequest request);

    void deleteCourse(Long id);
}
