package com.talent.management.features.class_management.controller;

import com.talent.management.features.class_management.service.ClassManagementService;
import com.talent.management.shared.config.WebSecurityConfig;
import com.talent.management.features.auth.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClassManagementController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(WebSecurityConfig.class)
class ClassManagementControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ClassManagementService service;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @WithMockUser(roles = "TEACHER")
    void teacherCannotReadManagementList() throws Exception {
        mvc.perform(get("/api/class-management/classes")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PARENT")
    void parentCannotCreateClass() throws Exception {
        mvc.perform(post("/api/class-management/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":1,"branchId":2,"classCode":"C-01","className":"Lớp mới",
                                 "maxStudents":8,"status":"OPEN","startDate":"2026-11-01","endDate":"2027-02-01"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCanReadManagementList() throws Exception {
        when(service.search(null, null, null, null)).thenReturn(List.of());
        mvc.perform(get("/api/class-management/classes")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "BRANCH_MANAGER")
    void branchManagerCanReadManagementListShownBySidebar() throws Exception {
        when(service.search(null, null, null, null)).thenReturn(List.of());
        mvc.perform(get("/api/class-management/classes")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void rejectsZeroCapacity() throws Exception {
        mvc.perform(post("/api/class-management/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":1,"branchId":2,"classCode":"C-01","className":"Lớp mới",
                                 "maxStudents":0,"status":"OPEN","startDate":"2026-11-01","endDate":"2027-02-01"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreateClass() throws Exception {
        mvc.perform(post("/api/class-management/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":1,"branchId":2,"classCode":"C-01","className":"Lớp mới",
                                 "maxStudents":8,"status":"OPEN","startDate":"2026-11-01","endDate":"2027-02-01"}
                                """))
                .andExpect(status().isCreated());
    }
}
