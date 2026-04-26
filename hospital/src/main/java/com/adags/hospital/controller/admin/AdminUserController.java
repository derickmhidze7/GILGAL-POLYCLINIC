package com.adags.hospital.controller.admin;

import com.adags.hospital.domain.user.Role;
import com.adags.hospital.dto.staff.StaffResponse;
import com.adags.hospital.dto.user.CreateUserRequest;
import com.adags.hospital.dto.user.UserResponse;
import com.adags.hospital.service.staff.StaffService;
import com.adags.hospital.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private final StaffService staffService;

    // ─── List users ────────────────────────────────────────────────────────────

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<UserResponse> usersPage = userService.getAll(
                PageRequest.of(page, 20, Sort.by("username"))
        );
        List<UserResponse> users = usersPage.getContent();

        List<StaffResponse> staffList = staffService.getAll(
                PageRequest.of(0, 200, Sort.by("lastName"))).getContent();

        model.addAttribute("users", users);
        model.addAttribute("staffList", staffList);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", usersPage.getTotalPages());
        model.addAttribute("totalUsers", usersPage.getTotalElements());
        model.addAttribute("roles", Role.values());
        model.addAttribute("activePage", "users");
        return "admin/users";
    }

    // ─── Create user ───────────────────────────────────────────────────────────

    @PostMapping
    public String createUser(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam Role role,
            @RequestParam(required = false) String staffId,
            RedirectAttributes ra) {

        try {
            UUID staffUuid = (staffId != null && !staffId.isBlank()) ? UUID.fromString(staffId) : null;
            CreateUserRequest req = new CreateUserRequest(username, email, password, role, staffUuid);
            userService.createUser(req);
            ra.addFlashAttribute("successMsg", "User '" + username + "' created successfully.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "Invalid Staff ID format.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ─── Toggle enabled / locked ───────────────────────────────────────────────

    @PostMapping("/{id}/toggle-status")
    public String toggleStatus(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            UserResponse updated = UserResponse.from(userService.toggleStatus(id));
            String status = updated.enabled() ? "enabled" : "disabled";
            ra.addFlashAttribute("successMsg", "User '" + updated.username() + "' " + status + ".");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ─── Reset password ────────────────────────────────────────────────────────

    @PostMapping("/{id}/reset-password")
    public String resetPassword(
            @PathVariable UUID id,
            @RequestParam String newPassword,
            RedirectAttributes ra) {

        try {
            userService.resetPassword(id, newPassword);
            ra.addFlashAttribute("successMsg", "Password reset successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ─── Edit user (email + role) ──────────────────────────────────────────────

    @PostMapping("/{id}/edit")
    public String editUser(
            @PathVariable UUID id,
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam Role role,
            RedirectAttributes ra) {

        try {
            UserResponse updated = UserResponse.from(userService.updateUser(id, username, email, role));
            ra.addFlashAttribute("successMsg", "User '" + updated.username() + "' updated successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ─── Delete user ───────────────────────────────────────────────────────────

    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            userService.deleteUser(id);
            ra.addFlashAttribute("successMsg", "User deleted successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/users";
    }
}
