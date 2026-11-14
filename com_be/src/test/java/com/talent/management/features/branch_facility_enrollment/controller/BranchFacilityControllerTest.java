package com.talent.management.features.branch_facility_enrollment.controller;

import com.talent.management.features.auth.security.JwtAuthenticationFilter;
import com.talent.management.features.branch_facility_enrollment.service.BranchFacilityService;
import com.talent.management.shared.config.WebSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BranchFacilityController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(WebSecurityConfig.class)
class BranchFacilityControllerTest {
    @Autowired MockMvc mvc;
    @MockBean BranchFacilityService service;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @WithMockUser(roles = "ADMIN")
    void branchReadsRemainAvailable() throws Exception {
        mvc.perform(get("/api/branches")).andExpect(status().isOk());
        mvc.perform(get("/api/branches/options")).andExpect(status().isOk());
        mvc.perform(get("/api/branches/1/rooms")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unsupportedMethodOnAnExistingUrlReturns405AndAdvertisesAllowedMethods() throws Exception {
        mvc.perform(post("/api/branches"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void removedUrlsReturn404() throws Exception {
        for (String path : new String[]{"/api/branches/1/rooms/options", "/api/branches/1/equipments"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
        for (String path : new String[]{"/api/branches/rooms", "/api/branches/equipments"}) {
            mvc.perform(post(path)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unexpectedServiceErrorsStillReturn500() throws Exception {
        when(service.getAllBranches()).thenThrow(new IllegalStateException("Unexpected failure"));
        mvc.perform(get("/api/branches")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCanInspectRoomUsageButCannotEditFacilities() throws Exception {
        mvc.perform(get("/api/branches/rooms/2/usage")).andExpect(status().isOk());
        mvc.perform(put("/api/branches/rooms/2").contentType(MediaType.APPLICATION_JSON).content("""
                {"roomName":"Room","capacity":8,"roomType":"CHESS_ROOM","status":"AVAILABLE"}
                """)).andExpect(status().isForbidden());
        mvc.perform(put("/api/branches/equipments/3").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Chess set","category":"CHESS_SET","status":"BROKEN"}
                """)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PARENT")
    void parentCanViewRoomsButCannotInspectOperationalSchedules() throws Exception {
        mvc.perform(get("/api/branches/1/rooms")).andExpect(status().isOk());
        mvc.perform(get("/api/branches/rooms/2/usage")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanRecordBrokenEquipmentAndChangeAnUnusedRoomToSwimmingPool() throws Exception {
        mvc.perform(put("/api/branches/rooms/2").contentType(MediaType.APPLICATION_JSON).content("""
                {"roomName":"Pool","capacity":8,"roomType":"SWIMMING_POOL","status":"MAINTENANCE"}
                """)).andExpect(status().isOk());
        mvc.perform(put("/api/branches/equipments/3").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Rescue equipment","category":"RESCUE_EQUIPMENT","status":"BROKEN"}
                """)).andExpect(status().isOk());
    }
}
