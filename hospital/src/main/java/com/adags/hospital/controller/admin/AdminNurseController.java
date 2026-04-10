package com.adags.hospital.controller.admin;

import com.adags.hospital.domain.common.Gender;
import com.adags.hospital.domain.user.Role;
import com.adags.hospital.dto.user.CreateNurseRequest;
import com.adags.hospital.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin/nurses")
@RequiredArgsConstructor
public class AdminNurseController {

    private final UserService userService;

    // ─── Show create form ──────────────────────────────────────────────────────

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("genders", Gender.values());
        model.addAttribute("activePage", "users");
        return "admin/nurse-form";
    }

    // ─── Handle form submission ────────────────────────────────────────────────

    @PostMapping
    public String createNurse(
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateOfBirth,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String phone,
            @RequestParam String email,
            @RequestParam(required = false) String licenseNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate employmentDate,
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(defaultValue = "WARD_NURSE") String nurseType,
            RedirectAttributes ra) {

        try {
            Gender genderEnum = (gender != null && !gender.isBlank()) ? Gender.valueOf(gender) : null;
            Role nurseRole = Role.valueOf(nurseType);

            CreateNurseRequest request = new CreateNurseRequest(
                    firstName, lastName, dateOfBirth, genderEnum,
                    phone, email, null, licenseNumber, employmentDate,
                    username, password
            );

            userService.createNurseWithRole(request, nurseRole);
            String roleLabel = nurseRole == Role.NURSE ? "Nurse" : "Ward Nurse";
            ra.addFlashAttribute("successMsg",
                    roleLabel + " '" + firstName + " " + lastName + "' created successfully. Username: " + username);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "Invalid selection: " + e.getMessage());
            return "redirect:/admin/nurses/new";
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/admin/nurses/new";
        }

        return "redirect:/admin/users";
    }
}
