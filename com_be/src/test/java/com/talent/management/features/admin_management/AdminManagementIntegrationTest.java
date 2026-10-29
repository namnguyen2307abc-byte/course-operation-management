package com.talent.management.features.admin_management;

import com.fasterxml.jackson.databind.*;
import com.talent.management.features.auth.repository.*;
import com.talent.management.features.auth.dto.LoginRequest;
import com.talent.management.features.auth.service.AuthService;
import com.talent.management.features.admin_management.repository.AdminAuditLogRepository;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-integration;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "app.seed.enabled=false" })
@AutoConfigureMockMvc @Transactional
class AdminManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired StudentRepository students;
    @Autowired AdminAuditLogRepository audit;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager em;
    @Autowired com.talent.management.features.admin_management.service.AdminUserService administration;
    @Autowired com.talent.management.features.class_management.service.ClassSchedulingService classScheduling;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    User admin;
    String adminToken;

    @BeforeEach void setup() {
        admin = user("test_admin", Role.ADMIN);
        adminToken = login(admin.getUsername(), "initial-pass");
    }

    User user(String username, Role role) {
        return users.saveAndFlush(User.builder().username(username).fullName(username).role(role)
                .password(encoder.encode("initial-pass")).status(UserStatus.ACTIVE).build());
    }
    String login(String username, String password) {
        LoginRequest request = new LoginRequest(); request.setUsername(username); request.setPassword(password);
        return auth.login(request).getToken();
    }
    ResultActions postJson(String path, String token, Map<String, ?> body) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mvc.perform(request);
    }
    ResultActions patchJson(String path, Map<String, ?> body) throws Exception {
        return mvc.perform(patch(path).header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    JsonNode body(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }

    @Test void teacherSpecialismCanBeManagedWithoutChangingPlacementDepartment() throws Exception {
        var created = body(postJson("/api/admin/users", adminToken, Map.of("username", "specific_teacher",
                "password", "temporary-pass", "fullName", "Specific teacher", "role", "TEACHER",
                "subject", "DAN", "teachingSubject", "GUITAR")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value("DAN"))
                .andExpect(jsonPath("$.teachingSubject").value("GUITAR")));
        long id = created.get("id").asLong();
        Course guitar = Course.builder().program(Program.builder().code("GUITAR").build()).build();
        Course piano = Course.builder().program(Program.builder().code("PIANO").build()).build();
        assertThat(classScheduling.isTeacherSuitable(guitar, users.findById(id).orElseThrow())).isTrue();
        assertThat(classScheduling.isTeacherSuitable(piano, users.findById(id).orElseThrow())).isFalse();
        mvc.perform(put("/api/admin/users/" + id).header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "fullName", "Specific teacher", "subject", "DAN", "teachingSubject", "PIANO"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subject").value("DAN"))
                .andExpect(jsonPath("$.teachingSubject").value("PIANO"));
        // Older clients omit the new field; a profile update must preserve it.
        mvc.perform(put("/api/admin/users/" + id).header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "fullName", "Updated teacher", "subject", "DAN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.teachingSubject").value("PIANO"));
        assertThat(classScheduling.isTeacherSuitable(piano, users.findById(id).orElseThrow())).isTrue();
        assertThat(audit.findAll().stream().anyMatch(a -> a.getDetails().contains("GUITAR")
                && a.getDetails().contains("PIANO"))).isTrue();
    }

    @Test void loginChecksPasswordAndNeverCreatesAnUnknownUser() throws Exception {
        long before = users.count();
        postJson("/api/auth/login", null, Map.of("username", "unknown_admin", "password", "initial-pass")).andExpect(status().isUnauthorized());
        postJson("/api/auth/login", null, Map.of("username", admin.getUsername(), "password", "wrong-pass")).andExpect(status().isUnauthorized());
        assertThat(users.count()).isEqualTo(before);
        postJson("/api/auth/login", null, Map.of("username", admin.getUsername(), "password", "initial-pass")).andExpect(status().isOk());
    }

    @Test void anonymousAndEveryNonAdminRoleAreRejectedAndFakeTokensFail() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        for (Role role : Role.values()) if (role != Role.ADMIN) {
            String token = login(user("test_" + role, role).getUsername(), "initial-pass");
            mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            postJson("/api/admin/users", token, Map.of()).andExpect(status().isForbidden());
        }
        for (String token : List.of(admin.getUsername(), "AUTH_TOKEN_" + admin.getUsername(), adminToken + "broken"))
            mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tuition-payment/stats")).andExpect(status().isUnauthorized());
    }

    @Test void publicRegistrationCannotChoosePrivilegedRolesOrReuseAnExistingAccount() throws Exception {
        Map<String, Object> request = new HashMap<>(Map.of("username", "new_parent", "password", "parent-pass", "fullName", "Phụ huynh"));
        request.put("role", "ADMIN"); postJson("/api/auth/register", null, request).andExpect(status().isBadRequest());
        request.put("role", "PARENT"); postJson("/api/auth/register", null, request).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("PARENT"));
        postJson("/api/auth/register", null, request).andExpect(status().isConflict());
        request.put("username", admin.getUsername()); postJson("/api/auth/register", null, request).andExpect(status().isConflict());
    }

    @Test void provisionedUserMustChangePasswordAndNeitherResponseNorAuditLeaksSecrets() throws Exception {
        var created = postJson("/api/admin/users", adminToken, Map.of("username", "new_staff", "password", "temporary-pass", "fullName", "Nhân viên mới", "role", "STAFF"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.mustChangePassword").value(true)).andExpect(jsonPath("$.password").doesNotExist());
        Long id = body(created).get("id").asLong();
        String temporaryToken = login("new_staff", "temporary-pass");
        mvc.perform(get("/api/tuition-payment/stats").header("Authorization", "Bearer " + temporaryToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        var changed = postJson("/api/auth/change-password", temporaryToken, Map.of("currentPassword", "temporary-pass", "newPassword", "chosen-new-pass"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(false));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + temporaryToken)).andExpect(status().isUnauthorized());
        String newToken = body(changed).get("token").asText();
        mvc.perform(get("/api/tuition-payment/stats").header("Authorization", "Bearer " + newToken)).andExpect(status().isOk());
        assertThat(encoder.matches("chosen-new-pass", users.findById(id).orElseThrow().getPassword())).isTrue();
        assertThat(audit.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getAction()).isEqualTo("CREATE_USER");
            assertThat(log.getDetails()).doesNotContain("temporary-pass", "chosen-new-pass", "$2a$");
        });
    }

    @Test void lockingUnlockingChangingRoleAndResettingPasswordRevokeOldTokens() throws Exception {
        User staff = user("managed_staff", Role.STAFF);
        String token = login(staff.getUsername(), "initial-pass");
        patchJson("/api/admin/users/" + staff.getId() + "/status", Map.of("status", "INACTIVE", "reason", "Ngừng truy cập")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        postJson("/api/auth/login", null, Map.of("username", staff.getUsername(), "password", "initial-pass")).andExpect(status().isUnauthorized());
        patchJson("/api/admin/users/" + staff.getId() + "/status", Map.of("status", "ACTIVE", "reason", "Cho phép truy cập")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        token = login(staff.getUsername(), "initial-pass");
        patchJson("/api/admin/users/" + staff.getId() + "/role", Map.of("role", "PARENT", "reason", "Chuyển vai trò")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        token = login(staff.getUsername(), "initial-pass");
        postJson("/api/admin/users/" + staff.getId() + "/reset-password", adminToken, Map.of("password", "replacement-pass", "reason", "Quên mật khẩu")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        assertThat(users.findById(staff.getId()).orElseThrow().isMustChangePassword()).isTrue();
        assertThat(audit.count()).isEqualTo(4);
    }

    @Test void adminCannotDisableOrDemoteSelfAndLinkedParentCannotChangeRole() throws Exception {
        patchJson("/api/admin/users/" + admin.getId() + "/status", Map.of("status", "INACTIVE", "reason", "Test")).andExpect(status().isConflict());
        patchJson("/api/admin/users/" + admin.getId() + "/role", Map.of("role", "STAFF", "reason", "Test")).andExpect(status().isConflict());
        User parent = user("linked_parent", Role.PARENT);
        students.saveAndFlush(Student.builder().fullName("Bé A").parent(parent).build());
        patchJson("/api/admin/users/" + parent.getId() + "/role", Map.of("role", "TEACHER", "reason", "Test")).andExpect(status().isConflict());
        assertThat(audit.count()).isZero();
    }

    @Test void independentStudentNeedsAnExplicitUniqueProfileAndSeesOnlyOwnProfile() throws Exception {
        Student child = students.saveAndFlush(Student.builder().fullName("Học viên A").build());
        students.saveAndFlush(Student.builder().fullName("Học viên B").build());
        Map<String, Object> request = new HashMap<>(Map.of("username", "student_a", "password", "student-pass", "fullName", "Học viên A", "role", "STUDENT"));
        postJson("/api/admin/users", adminToken, request).andExpect(status().isConflict());
        assertThat(users.findByUsername("student_a")).isEmpty();
        // A failed transaction is rolled back by the service; start a fresh transaction for the successful path in a separate test.
    }

    @Test void studentAccountLinksAndDuplicateProfileIsRejected() throws Exception {
        Student child = students.saveAndFlush(Student.builder().fullName("Học viên A").build());
        students.saveAndFlush(Student.builder().fullName("Học viên B").build());
        var result = postJson("/api/admin/users", adminToken, Map.of("username", "student_a", "password", "student-pass", "fullName", "Học viên A", "role", "STUDENT", "studentId", child.getId())).andExpect(status().isCreated());
        String initial = login("student_a", "student-pass");
        String token = body(postJson("/api/auth/change-password", initial, Map.of("currentPassword", "student-pass", "newPassword", "student-new-pass")).andExpect(status().isOk())).get("token").asText();
        mvc.perform(get("/api/attendance-makeup/students").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(child.getId()));
        mvc.perform(get("/api/admin/users/" + body(result).get("id").asLong()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studentProfiles[0].id").value(child.getId()));
        postJson("/api/admin/users", adminToken, Map.of("username", "student_b", "password", "student-pass", "fullName", "Học viên B", "role", "STUDENT", "studentId", child.getId())).andExpect(status().isConflict());
    }

    @Test void dashboardExcludesFailedPaymentsUsesExclusiveEndDateAndShowsOutstanding() throws Exception {
        Student child = students.saveAndFlush(Student.builder().fullName("Học viên thống kê").build());
        Invoice unpaid = Invoice.builder().invoiceCode("UNPAID-TEST").student(child).originalAmount(new BigDecimal("500"))
                .finalAmount(new BigDecimal("400")).dueDate(LocalDate.now().minusDays(1)).status(InvoiceStatus.UNPAID).build();
        em.persist(unpaid);
        for (int i = 0; i < 3; i++) {
            Invoice invoice = Invoice.builder().invoiceCode("PAID-" + i).student(child).originalAmount(new BigDecimal("100"))
                    .finalAmount(new BigDecimal("100")).status(InvoiceStatus.PAID).build(); em.persist(invoice);
            em.persist(Payment.builder().invoice(invoice).paymentCode("TEST-" + i).paymentMethod(PaymentMethod.CASH_AT_DESK)
                    .amount(new BigDecimal("100")).status(i == 1 ? PaymentStatus.FAILED : PaymentStatus.SUCCESS)
                    .paymentDate(i == 2 ? LocalDate.of(2026, 10, 26).atStartOfDay() : LocalDate.of(2026, 10, 25).atTime(23, 59, 59, 999000000)).build());
        }
        em.flush();
        mvc.perform(get("/api/admin/dashboard?from=2026-10-25&to=2026-10-25").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revenue").value(100))
                .andExpect(jsonPath("$.successfulTransactions").value(1)).andExpect(jsonPath("$.outstandingAmount").value(400))
                .andExpect(jsonPath("$.overdueAmount").value(400)).andExpect(jsonPath("$.revenueByDay.length()").value(1));
        mvc.perform(get("/api/admin/dashboard?from=2026-10-26&to=2026-10-25").header("Authorization", "Bearer " + adminToken)).andExpect(status().isBadRequest());
    }

    @Test void listFiltersArePaginatedAndAuditCanBeFilteredByTarget() throws Exception {
        user("teacher_one", Role.TEACHER); user("teacher_two", Role.TEACHER);
        mvc.perform(get("/api/admin/users?role=TEACHER&keyword=teacher&size=1&page=1").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.hasPrevious").value(true));
        var created = postJson("/api/admin/users", adminToken, Map.of("username", "audited_staff", "password", "temporary-pass", "fullName", "Nhân viên", "role", "STAFF")).andExpect(status().isCreated());
        mvc.perform(get("/api/admin/audit-logs?targetId=" + body(created).get("id").asLong()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].actorUsername").value(admin.getUsername()));
    }

    @Test void administrationListsDefaultToFiveRowsAndKeepTheRemainingRowsOnTheNextPage() throws Exception {
        for (int i = 0; i < 6; i++) {
            User created = user("pagination_staff_" + i, Role.STAFF);
            audit.save(AdminAuditLog.builder().actorId(admin.getId()).actorUsername(admin.getUsername())
                    .targetId(created.getId()).targetUsername(created.getUsername()).action("CREATE_USER")
                    .details("Integration pagination fixture").createdAt(LocalDateTime.now()).build());
        }
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.items.length()").value(5)).andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get("/api/admin/users?page=1").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.last").value(true));
        mvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.items.length()").value(5)).andExpect(jsonPath("$.totalElements").value(6));
        mvc.perform(get("/api/admin/audit-logs?page=1").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.last").value(true));
    }

    @Test @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void simultaneousAdminDemotionsCannotRemoveTheLastAdministrator() throws Exception {
        User second = user("concurrent_admin", Role.ADMIN);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var firstResult = executor.submit(() -> demoteAfter(start, admin, second));
            var secondResult = executor.submit(() -> demoteAfter(start, second, admin));
            start.countDown();
            assertThat(List.of(firstResult.get(15, java.util.concurrent.TimeUnit.SECONDS), secondResult.get(15, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 403);
            assertThat(users.findByRole(Role.ADMIN).stream().filter(u -> u.getStatus() == UserStatus.ACTIVE).count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
                audit.deleteAll(); users.deleteById(second.getId()); users.deleteById(admin.getId());
            });
        }
    }

    private int demoteAfter(java.util.concurrent.CountDownLatch start, User actor, User target) throws Exception {
        start.await();
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(actor.getUsername(), null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try {
            administration.changeRole(target.getId(), new com.talent.management.features.admin_management.dto.request.ChangeUserRoleRequest(Role.STAFF, null, "Concurrent demotion test"));
            return 200;
        } catch (org.springframework.web.server.ResponseStatusException ex) { return ex.getStatusCode().value(); }
        finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
}
