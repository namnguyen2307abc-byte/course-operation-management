package com.example.courseoperationmanagement.controller;

import com.example.courseoperationmanagement.dto.ChangePasswordRequest;
import com.example.courseoperationmanagement.entity.User;
import com.example.courseoperationmanagement.exception.InvalidCurrentPasswordException;
import com.example.courseoperationmanagement.service.UserService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @GetMapping("/profile")
    public String profile(Principal principal, Model model) {
        User user = userService.findByEmail(principal.getName());
        model.addAttribute("user", user);
        return "user/profile";
    }

    @GetMapping("/profile/change-password")
    public String changePasswordForm(Model model) {
        if (!model.containsAttribute("changePasswordRequest")) {
            model.addAttribute("changePasswordRequest", new ChangePasswordRequest());
        }
        return "user/change-password";
    }

    @PostMapping("/profile/change-password")
    public String changePassword(
            Principal principal,
            @Valid @ModelAttribute("changePasswordRequest") ChangePasswordRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        validatePasswordConfirmation(request, bindingResult);

        if (bindingResult.hasErrors()) {
            return "user/change-password";
        }

        try {
            userService.changePassword(principal.getName(), request);
        } catch (InvalidCurrentPasswordException exception) {
            bindingResult.rejectValue("currentPassword", "password.invalid", exception.getMessage());
            return "user/change-password";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Password changed successfully.");
        return "redirect:/profile";
    }

    private void validatePasswordConfirmation(ChangePasswordRequest request, BindingResult bindingResult) {
        if (request.getNewPassword() != null && !request.getNewPassword().equals(request.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Passwords do not match.");
        }
    }
}
