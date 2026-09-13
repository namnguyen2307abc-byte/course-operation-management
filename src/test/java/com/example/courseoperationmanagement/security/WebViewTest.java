package com.example.courseoperationmanagement.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.courseoperationmanagement.entity.Role;
import com.example.courseoperationmanagement.entity.User;
import com.example.courseoperationmanagement.repository.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class WebViewTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(username = "member@example.com", roles = "STUDENT")
    void profileRendersAccountDataWithoutAdminNavigation() throws Exception {
        when(userRepository.findByEmail("member@example.com")).thenReturn(Optional.of(member()));
        snapshot("profile", mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alex Nguyen")))
                .andExpect(content().string(not(containsString("href=\"/admin/users\""))))
                .andReturn());
        snapshot("change-password", mockMvc.perform(get("/profile/change-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/profile/change-password\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andReturn());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void directoryRendersDataAndPreservesFiltersInPagination() throws Exception {
        when(userRepository.searchUsers(any(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(member()), PageRequest.of(1, 10), 31));
        snapshot("users", mockMvc.perform(get("/admin/users").param("keyword", "Alex").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Alex\"")))
                .andExpect(content().string(containsString("page=2&amp;keyword=Alex")))
                .andExpect(content().string(containsString("aria-current=\"page\"")))
                .andReturn());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void userDetailsRendersSecuredManagementForms() throws Exception {
        when(userRepository.findById(42L)).thenReturn(Optional.of(member()));
        snapshot("user-detail", mockMvc.perform(get("/admin/users/42"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/admin/users/42/role\"")))
                .andExpect(content().string(containsString("action=\"/admin/users/42/deactivate\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andReturn());
        snapshot("admin-home", mockMvc.perform(get("/")).andExpect(status().isOk()).andReturn());
    }

    @Test
    void registrationErrorsRemainVisibleAndAssociatedWithFields() throws Exception {
        snapshot("register-errors", mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Alex Nguyen")
                        .param("email", "member@example.com")
                        .param("password", "a-valid-password")
                        .param("confirmPassword", "different-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"confirmPassword-error\"")))
                .andExpect(content().string(containsString("Passwords do not match.")))
                .andReturn());
    }

    @Test
    void localAssetsAndLoginFeedbackAreAccessibleWithoutAuthentication() throws Exception {
        for (String path : List.of("/css/app.css", "/css/bootstrap.min.css", "/js/app.js",
                "/js/lucide.min.js", "/images/campus.jpg")) {
            mockMvc.perform(get(path)).andExpect(status().isOk());
        }
        snapshot("login-error", mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Invalid email or password")))
                .andReturn());
    }

    private User member() {
        User user = new User();
        user.setId(42L);
        user.setFullName("Alex Nguyen");
        user.setEmail("member@example.com");
        user.setRole(Role.STUDENT);
        user.setCreatedAt(LocalDateTime.of(2026, 9, 1, 9, 30));
        user.setUpdatedAt(LocalDateTime.of(2026, 9, 12, 14, 15));
        return user;
    }

    private void snapshot(String name, MvcResult result) throws Exception {
        if (Boolean.getBoolean("ui.snapshots")) {
            Path directory = Path.of("target", "ui-snapshots");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(name + ".html"), result.getResponse().getContentAsString());
        }
    }
}
