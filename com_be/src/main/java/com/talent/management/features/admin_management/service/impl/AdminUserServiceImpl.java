package com.talent.management.features.admin_management.service.impl;

import com.talent.management.features.admin_management.dto.request.*;
import com.talent.management.features.admin_management.dto.response.*;
import com.talent.management.features.admin_management.dto.response.AdminUserDetailResponse.LinkedRecord;
import com.talent.management.features.admin_management.mapper.AdminUserMapper;
import com.talent.management.features.admin_management.repository.*;
import com.talent.management.features.admin_management.service.*;
import com.talent.management.shared.dto.PageResponse;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import com.talent.management.shared.service.CurrentUserService;
import jakarta.persistence.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class AdminUserServiceImpl implements AdminUserService {
    private final AdminUserRepository repository;
    private final AdminAccountLinkRepository links;
    private final AdminUserMapper mapper;
    private final AdminAuditService audit;
    private final CurrentUserService currentUser;
    private final PasswordEncoder encoder;
    @PersistenceContext private EntityManager em;

    @Override
    public PageResponse<AdminUserResponse> search(String keyword, Role role, UserStatus status, int page, int size) {
        var result = repository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) predicates.add(cb.equal(root.get("role"), role));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (keyword != null && !keyword.isBlank()) {
                String value = "%" + keyword.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("username")), value, '\\'), cb.like(cb.lower(root.get("fullName")), value, '\\'),
                        cb.like(cb.lower(root.get("email")), value, '\\'), cb.like(root.get("phone"), value, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        }, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return PageResponse.from(result.map(mapper::toResponse));
    }

    @Override
    public AdminUserDetailResponse detail(Long id) {
        User user = find(id);
        return new AdminUserDetailResponse(mapper.toResponse(user), links.classes(id), links.children(id), links.studentProfiles(id));
    }

    @Override
    public List<LinkedRecord> studentOptions(String keyword) {
        String pattern = "%" + (keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")) + "%";
        return em.createQuery("select s.id, s.fullName from Student s where s.account is null and lower(s.fullName) like :keyword escape '\\' order by s.fullName, s.id", Object[].class)
                .setParameter("keyword", pattern).setMaxResults(50).getResultList().stream().map(r -> new LinkedRecord((Long) r[0], (String) r[1])).toList();
    }

    @Override @Transactional
    public AdminUserResponse create(CreateAdminUserRequest request) {
        User actor = lockActor();
        String username = request.username().trim();
        if (request.role() == Role.STUDENT && request.studentId() == null) conflict("Cần chọn hồ sơ học viên cho tài khoản STUDENT");
        if (repository.existsByUsernameIgnoreCase(username)) conflict("Tên đăng nhập đã được sử dụng");
        checkEmail(request.email(), -1L);
        User user = User.builder().username(username).password(encoder.encode(request.password()))
                .fullName(request.fullName().trim()).email(clean(request.email())).phone(clean(request.phone()))
                .role(request.role()).subject(clean(request.subject()))
                .teachingSubject(request.role() == Role.TEACHER ? clean(request.teachingSubject()) : null)
                .status(UserStatus.ACTIVE).mustChangePassword(true).build();
        repository.saveAndFlush(user);
        if (request.role() == Role.STUDENT) linkStudent(user, request.studentId());
        else if (request.studentId() != null) conflict("Chỉ tài khoản học viên được liên kết hồ sơ học viên độc lập");
        audit.record(actor, user, "CREATE_USER", "Cấp tài khoản; role=" + user.getRole() + "; trạng thái=ACTIVE");
        return mapper.toResponse(user);
    }

    @Override @Transactional
    public AdminUserResponse update(Long id, UpdateAdminUserRequest request) {
        User actor = lockActor();
        User user = lockTarget(id);
        checkEmail(request.email(), id);
        String before = profile(user);
        user.setFullName(request.fullName().trim()); user.setEmail(clean(request.email()));
        user.setPhone(clean(request.phone())); user.setSubject(clean(request.subject()));
        // Omitted by older clients: preserve the existing exact specialism.
        if (user.getRole() == Role.TEACHER && request.teachingSubject() != null) {
            user.setTeachingSubject(clean(request.teachingSubject()));
        }
        audit.record(actor, user, "UPDATE_USER", before + " → " + profile(user));
        return mapper.toResponse(user);
    }

    @Override @Transactional
    public AdminUserResponse changeRole(Long id, ChangeUserRoleRequest request) {
        User actor = lockActor(); User user = lockTarget(id);
        if (user.getRole() == request.role()) return mapper.toResponse(user);
        protectAdmin(actor, user);
        if (user.getRole() == Role.TEACHER && links.hasTeacherAssignments(id)) conflict("Giáo viên còn liên kết lớp, lịch dạy hoặc đánh giá. Cần xử lý liên kết trước khi đổi role");
        if (user.getRole() == Role.PARENT && !links.children(id).isEmpty()) conflict("Phụ huynh còn hồ sơ con. Cần chuyển liên kết trước khi đổi role");
        if (user.getRole() == Role.STUDENT && !links.studentProfiles(id).isEmpty()) conflict("Tài khoản còn liên kết hồ sơ học viên, không thể đổi role");
        if (request.role() == Role.STUDENT) linkStudent(user, request.studentId());
        Role old = user.getRole(); user.setRole(request.role()); revoke(user);
        audit.record(actor, user, "CHANGE_ROLE", old + " → " + request.role() + "; lý do: " + request.reason().trim());
        return mapper.toResponse(user);
    }

    @Override @Transactional
    public AdminUserResponse changeStatus(Long id, ChangeUserStatusRequest request) {
        User actor = lockActor(); User user = lockTarget(id);
        if (user.getStatus() == request.status()) return mapper.toResponse(user);
        if (request.status() == UserStatus.INACTIVE) protectAdmin(actor, user);
        UserStatus old = user.getStatus(); user.setStatus(request.status()); revoke(user);
        audit.record(actor, user, "CHANGE_STATUS", old + " → " + request.status() + "; lý do: " + request.reason().trim());
        return mapper.toResponse(user);
    }

    @Override @Transactional
    public AdminUserResponse resetPassword(Long id, ResetUserPasswordRequest request) {
        User actor = lockActor(); User user = lockTarget(id);
        if (actor.getId().equals(id)) conflict("Hãy sử dụng chức năng đổi mật khẩu cho tài khoản của bạn");
        user.setPassword(encoder.encode(request.password())); user.setMustChangePassword(true); revoke(user);
        audit.record(actor, user, "RESET_PASSWORD", "Yêu cầu đổi mật khẩu ở lần đăng nhập tiếp theo; lý do: " + request.reason().trim());
        return mapper.toResponse(user);
    }

    private User lockActor() {
        // Every administrative mutation locks administrators first, then the target, in the same order.
        List<User> admins = repository.lockAdministrators();
        Long actorId = currentUser.getCurrentUser().getId();
        return admins.stream().filter(u -> u.getId().equals(actorId) && u.getRole() == Role.ADMIN && u.getStatus() == UserStatus.ACTIVE)
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ admin đang hoạt động được quản lý tài khoản"));
    }
    private void protectAdmin(User actor, User user) {
        if (actor.getId().equals(user.getId())) conflict("Không được tự khóa hoặc tự thay đổi role của mình");
        if (user.getRole() == Role.ADMIN && user.getStatus() == UserStatus.ACTIVE
                && repository.count((root, query, cb) -> cb.and(cb.equal(root.get("role"), Role.ADMIN), cb.equal(root.get("status"), UserStatus.ACTIVE))) <= 1)
            conflict("Phải giữ ít nhất một admin đang hoạt động");
    }
    private void linkStudent(User user, Long studentId) {
        if (studentId == null) conflict("Cần chọn hồ sơ học viên cho tài khoản STUDENT");
        Student student = em.find(Student.class, studentId, LockModeType.PESSIMISTIC_WRITE);
        if (student == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ học viên");
        if (student.getAccount() != null && !student.getAccount().getId().equals(user.getId())) conflict("Hồ sơ học viên đã có tài khoản");
        student.setAccount(user);
    }
    private void checkEmail(String value, Long id) {
        String email = clean(value);
        if (email != null && repository.existsByEmailIgnoreCaseAndIdNot(email, id)) conflict("Email đã được sử dụng");
    }
    private User find(Long id) { return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản")); }
    private User lockTarget(Long id) { return repository.lockById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản")); }
    private void revoke(User user) { user.setTokenVersion(user.getTokenVersion() + 1); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String profile(User u) { return "họ tên=" + u.getFullName() + "; email=" + u.getEmail() + "; điện thoại=" + u.getPhone() + "; bộ môn=" + u.getSubject() + "; chuyên môn giảng dạy=" + u.getTeachingSubject(); }
    private void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
