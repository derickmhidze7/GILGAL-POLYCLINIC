package com.adags.hospital.controller.admin;

import com.adags.hospital.domain.common.Gender;
import com.adags.hospital.domain.user.Role;
import com.adags.hospital.dto.staff.StaffRequest;
import com.adags.hospital.dto.staff.StaffResponse;
import com.adags.hospital.repository.staff.DepartmentRepository;
import com.adags.hospital.service.staff.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.UUID;

@Controller
@RequestMapping("/admin/staff")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminStaffController {

    private final StaffService staffService;
    private final DepartmentRepository departmentRepository;

    // ─── List ──────────────────────────────────────────────────────────────────

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false) String search,
                       Model model) {
        Page<StaffResponse> staffPage;
        if (search != null && !search.isBlank()) {
            staffPage = staffService.search(search, PageRequest.of(page, 20, Sort.by("lastName")));
        } else {
            staffPage = staffService.getAll(PageRequest.of(page, 20, Sort.by("lastName")));
        }
        model.addAttribute("staffList", staffPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", staffPage.getTotalPages());
        model.addAttribute("totalStaff", staffPage.getTotalElements());
        model.addAttribute("search", search);
        model.addAttribute("roles", Role.values());
        model.addAttribute("genders", Gender.values());
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("activePage", "staff");
        return "admin/staff";
    }

    // ─── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    public String create(
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateOfBirth,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String phone,
            @RequestParam String email,
            @RequestParam(required = false) String departmentId,
            @RequestParam String staffRole,
            @RequestParam(required = false) String specialization,
            @RequestParam(required = false) String licenseNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate employmentDate,
            RedirectAttributes ra) {
        try {
            Gender genderEnum = (gender != null && !gender.isBlank()) ? Gender.valueOf(gender) : null;
            Role roleEnum = Role.valueOf(staffRole);
            UUID deptId = (departmentId != null && !departmentId.isBlank()) ? UUID.fromString(departmentId) : null;

            StaffRequest req = new StaffRequest(
                    firstName, lastName, dateOfBirth, genderEnum, phone,
                    email, deptId, roleEnum, specialization, licenseNumber, employmentDate);
            staffService.create(req);
            ra.addFlashAttribute("successMsg",
                    "Staff member '" + firstName + " " + lastName + "' created successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/staff";
    }

    // ─── Edit ──────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/edit")
    public String edit(
            @PathVariable UUID id,
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam String email,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateOfBirth,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String departmentId,
            @RequestParam String staffRole,
            @RequestParam(required = false) String specialization,
            @RequestParam(required = false) String licenseNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate employmentDate,
            RedirectAttributes ra) {
        try {
            Gender genderEnum = (gender != null && !gender.isBlank()) ? Gender.valueOf(gender) : null;
            Role roleEnum = Role.valueOf(staffRole);
            UUID deptId = (departmentId != null && !departmentId.isBlank()) ? UUID.fromString(departmentId) : null;

            StaffRequest req = new StaffRequest(
                    firstName, lastName, dateOfBirth, genderEnum, phone,
                    email, deptId, roleEnum, specialization, licenseNumber, employmentDate);
            staffService.update(id, req);
            ra.addFlashAttribute("successMsg", "Staff member updated successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/staff";
    }

    // ─── Deactivate ────────────────────────────────────────────────────────────

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            staffService.deactivate(id);
            ra.addFlashAttribute("successMsg", "Staff member deactivated.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/staff";
    }
}
