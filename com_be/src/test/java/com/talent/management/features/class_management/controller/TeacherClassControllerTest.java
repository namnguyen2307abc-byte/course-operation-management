package com.talent.management.features.class_management.controller;

import com.talent.management.features.class_management.service.TeacherClassService;
import com.talent.management.features.auth.security.JwtAuthenticationFilter;
import com.talent.management.shared.config.WebSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TeacherClassController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(WebSecurityConfig.class)
class TeacherClassControllerTest {
    @Autowired MockMvc mvc;
    @MockBean TeacherClassService service;
    @MockBean JwtAuthenticationFilter filter;

    @Test @WithMockUser(roles = "PARENT")
    void parentCannotReadWholeClassOrOtherFamilies() throws Exception {
        mvc.perform(get("/api/teacher/classes")).andExpect(status().isForbidden());
        mvc.perform(get("/api/teacher/classes/1")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(roles = "STUDENT")
    void studentCannotReadWholeClass() throws Exception {
        mvc.perform(get("/api/teacher/classes/1")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(roles = "TEACHER")
    void teacherCanReachOwnershipCheckedService() throws Exception {
        mvc.perform(get("/api/teacher/classes")).andExpect(status().isOk());
        verify(service).getTeacherClasses(null);
    }
}
