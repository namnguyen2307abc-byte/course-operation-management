package com.example.courseoperationmanagement.controller;

import com.example.courseoperationmanagement.entity.Role;
import com.example.courseoperationmanagement.entity.User;
import com.example.courseoperationmanagement.entity.UserStatus;
import com.example.courseoperationmanagement.service.UserService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private static final int PAGE_SIZE = 10;

    private final UserService userService;

    @GetMapping
    public String users(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        int safePage = Math.max(page, 0);
        PageRequest pageable = PageRequest.of(safePage, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Role roleFilter = parseEnum(role, Role.class);
        UserStatus statusFilter = parseEnum(status, UserStatus.class);
        Page<User> users = userService.searchUsers(keyword, roleFilter, statusFilter, pageable);

        model.addAttribute("users", users);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedRole", role);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("roles", Role.values());
        model.addAttribute("statuses", UserStatus.values());
        return "admin/users";
    }

    @GetMapping("/{id}")
    public String userDetails(@PathVariable Long id, Model model) {
        model.addAttribute("user", userService.findById(id));
        model.addAttribute("roles", Role.values());
        return "admin/user-detail";
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        userService.activate(id);
        redirectAttributes.addFlashAttribute("successMessage", "User activated successfully.");
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(
            @PathVariable Long id,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        User user = userService.findById(id);
        if (user.getEmail().equals(principal.getName())) {
            redirectAttributes.addFlashAttribute("errorMessage", "You cannot deactivate your own account.");
            return "redirect:/admin/users/" + id;
        }

        userService.deactivate(id);
        redirectAttributes.addFlashAttribute("successMessage", "User deactivated successfully.");
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/role")
    public String changeRole(
            @PathVariable Long id,
            @RequestParam Role role,
            RedirectAttributes redirectAttributes) {
        userService.changeRole(id, role);
        redirectAttributes.addFlashAttribute("successMessage", "User role updated successfully.");
        return "redirect:/admin/users/" + id;
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumType) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
