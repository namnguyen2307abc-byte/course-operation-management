package com.talent.management.shared.service;

import com.talent.management.features.auth.security.JwtAuthenticationFilter;
import com.talent.management.features.branch_facility_enrollment.controller.FacilityBrowsingController;
import com.talent.management.features.branch_facility_enrollment.service.FacilityBrowsingService;
import com.talent.management.features.class_management.controller.ClassManagementController;
import com.talent.management.features.class_management.service.ClassManagementService;
import com.talent.management.features.course_enrollment.controller.CourseEnrollmentController;
import com.talent.management.features.course_enrollment.service.CourseEnrollmentService;
import com.talent.management.shared.config.WebSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({FacilityBrowsingController.class, ClassManagementController.class, CourseEnrollmentController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(WebSecurityConfig.class)
class ListPagingControllerTest {
    @Autowired MockMvc mvc;
    @MockBean FacilityBrowsingService facilities;
    @MockBean ClassManagementService classes;
    @MockBean CourseEnrollmentService enrollment;
    @MockBean JwtAuthenticationFilter jwt;

    @Test
    @WithMockUser(roles = "PARENT")
    void parentCanReadOwnRequestsAndPublicEquipmentButNotStaffOrClassTables() throws Exception {
        mvc.perform(get("/api/course-enrollment/requests/my/page")).andExpect(status().isOk());
        verify(enrollment).searchRequests(true, null, null, 0, 10);
        mvc.perform(get("/api/branches/rooms/2/equipments/page")).andExpect(status().isOk());
        mvc.perform(get("/api/branches/rooms/2/detail")).andExpect(status().isOk());
        mvc.perform(get("/api/branches/rooms/2/overview")).andExpect(status().isForbidden());
        mvc.perform(get("/api/branches/rooms/2/classes/page")).andExpect(status().isForbidden());
        mvc.perform(get("/api/branches/rooms/2/lessons/page")).andExpect(status().isForbidden());
        mvc.perform(get("/api/class-management/classes/page")).andExpect(status().isForbidden());
        mvc.perform(get("/api/course-enrollment/staff/requests/page")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCanBrowseQueueAndRoomsButCannotImpersonateParent() throws Exception {
        mvc.perform(get("/api/course-enrollment/staff/requests/page").param("page", "2").param("size", "5")).andExpect(status().isOk());
        verify(enrollment).searchRequests(false, null, null, 2, 5);
        mvc.perform(get("/api/course-enrollment/requests/my/page")).andExpect(status().isForbidden());
        mvc.perform(get("/api/class-management/classes/page")).andExpect(status().isOk());
        verify(classes).searchPage(null, null, null, null, 0, 10, "newest");
        mvc.perform(get("/api/branches/rooms/2/lessons/page")).andExpect(status().isOk());
        mvc.perform(get("/api/branches/1/rooms/page").param("status", "FAKE")).andExpect(status().isBadRequest());
    }
}
