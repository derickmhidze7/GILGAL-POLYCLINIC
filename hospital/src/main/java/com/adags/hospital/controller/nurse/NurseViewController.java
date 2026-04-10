package com.adags.hospital.controller.nurse;

import com.adags.hospital.domain.appointment.Appointment;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.triage.ModeOfAmbulation;
import com.adags.hospital.domain.triage.TriagePriority;
import com.adags.hospital.domain.user.AppUser;
import com.adags.hospital.domain.user.Role;
import com.adags.hospital.dto.triage.TriageRequest;
import com.adags.hospital.exception.ResourceNotFoundException;
import com.adags.hospital.repository.appointment.AppointmentRepository;
import com.adags.hospital.repository.staff.StaffRepository;
import com.adags.hospital.repository.triage.TriageRepository;
import com.adags.hospital.repository.user.UserRepository;
import com.adags.hospital.service.triage.TriageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/nurse")
@RequiredArgsConstructor
public class NurseViewController {

    private final TriageService triageService;
    private final AppointmentRepository appointmentRepository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final TriageRepository triageRepository;

    // -----------------------------------------------------------------------
    // Dashboard — triage queue
    // -----------------------------------------------------------------------

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<Appointment> queue = triageService.getActiveTriageQueue();
        List<Appointment> assessedQueue = triageService.getAssessedAwaitingDoctorQueue();
        model.addAttribute("queue", queue);
        model.addAttribute("assessedQueue", assessedQueue);
        model.addAttribute("activePage", "dashboard");
        return "nurse/dashboard";
    }

    @GetMapping("/completed-assessments")
    public String completedAssessments(Model model) {
        List<Appointment> assessedQueue = triageService.getAssessedAwaitingDoctorQueue();
        model.addAttribute("assessedQueue", assessedQueue);
        model.addAttribute("activePage", "completed");
        return "nurse/completed-assessments";
    }

    @GetMapping("/history")
    public String history(Model model, Authentication auth) {
        AppUser currentUser = userRepository.findByUsernameWithStaff(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Current user not found: " + auth.getName()));
        Staff nurse = currentUser.getStaff();
        if (nurse == null) {
            model.addAttribute("errorMsg", "No staff profile linked to your account.");
            model.addAttribute("history", java.util.Collections.emptyList());
        } else {
            model.addAttribute("history", triageService.getMyHistory(nurse.getId()));
        }
        model.addAttribute("activePage", "history");
        return "nurse/history";
    }

    @GetMapping("/completed-assessments/{appointmentId}")
    public String viewAssessment(@PathVariable UUID appointmentId, Model model) {
        Appointment appointment = appointmentRepository.findByIdWithPatient(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));
        model.addAttribute("triage", triageService.getByAppointmentId(appointmentId));
        model.addAttribute("appointment", appointment);
        model.addAttribute("patient", appointment.getPatient());
        model.addAttribute("activePage", "completed");
        return "nurse/assessment-detail";
    }

    // -----------------------------------------------------------------------
    // Assessment form
    // -----------------------------------------------------------------------

    @GetMapping("/assessment/{appointmentId}")
    public String assessmentForm(@PathVariable UUID appointmentId, Model model,
                                 Authentication auth, RedirectAttributes redirectAttrs) {
        Appointment appointment = appointmentRepository.findByIdWithPatient(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));

        // Block re-assessment: if a triage record already exists, redirect nurse away
        if (triageRepository.findByAppointmentId(appointmentId).isPresent()) {
            redirectAttrs.addFlashAttribute("errorMsg",
                "Assessment already completed for " +
                appointment.getPatient().getFirstName() + " " + appointment.getPatient().getLastName() +
                ". They are now awaiting the doctor / payment.");
            return "redirect:/nurse/dashboard";
        }

        // Doctors & specialists for referral dropdown
        List<Staff> doctors = new ArrayList<>(staffRepository.findByStaffRoleAndActiveTrue(Role.DOCTOR));
        doctors.addAll(staffRepository.findByStaffRoleAndActiveTrue(Role.SPECIALIST_DOCTOR));

        model.addAttribute("appointment", appointment);
        model.addAttribute("patient", appointment.getPatient());
        model.addAttribute("doctors", doctors);
        model.addAttribute("ambulationValues", ModeOfAmbulation.values());
        model.addAttribute("priorityValues", TriagePriority.values());
        model.addAttribute("activePage", "queue");
        return "nurse/assessment";
    }

    @PostMapping("/assessment/{appointmentId}")
    public String submitAssessment(
            @PathVariable UUID appointmentId,
            @RequestParam(required = false) String chiefComplaint,
            // Vital signs
            @RequestParam(required = false) BigDecimal temperature,
            @RequestParam(required = false) Integer bloodPressureSystolic,
            @RequestParam(required = false) Integer bloodPressureDiastolic,
            @RequestParam(required = false) Integer pulseRate,
            @RequestParam(required = false) Integer respiratoryRate,
            @RequestParam(required = false) BigDecimal oxygenSaturation,
            // Anthropometric
            @RequestParam(required = false) BigDecimal weight,
            @RequestParam(required = false) BigDecimal height,
            @RequestParam(required = false) BigDecimal bmi,
            // Medical history
            @RequestParam(required = false) String knownAllergies,
            @RequestParam(required = false) String comorbidities,
            @RequestParam(required = false) String currentSymptoms,
            @RequestParam(required = false) ModeOfAmbulation modeOfAmbulation,
            // Pain
            @RequestParam(defaultValue = "false") boolean hasPain,
            @RequestParam(required = false) Integer painScore,
            @RequestParam(required = false) String painLocation,
            // Risk
            @RequestParam(defaultValue = "false") boolean fallRisk,
            @RequestParam(required = false) Integer fallScore,
            // Infectious disease
            @RequestParam(defaultValue = "false") boolean infectiousDiseaseRisk,
            // Priority & notes
            @RequestParam TriagePriority triagePriority,
            @RequestParam(required = false) String notes,
            // Referral
            @RequestParam(required = false) UUID referredDoctorId,
            @RequestParam(required = false) String referralType,
            RedirectAttributes redirectAttrs,
            Authentication auth) {

        try {
            Appointment appointment = appointmentRepository.findById(appointmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));

            // Guard against duplicate assessment (e.g. form double-submit or direct POST)
            if (triageRepository.findByAppointmentId(appointmentId).isPresent()) {
                redirectAttrs.addFlashAttribute("errorMsg",
                    "Assessment has already been completed for this patient.");
                return "redirect:/nurse/dashboard";
            }

            // Resolve the currently logged-in nurse's Staff record (JOIN FETCH to avoid lazy-load)
            AppUser currentUser = userRepository.findByUsernameWithStaff(auth.getName())
                    .orElseThrow(() -> new IllegalStateException("Current user not found: " + auth.getName()));
            Staff nurse = currentUser.getStaff();
            if (nurse == null) {
                throw new IllegalStateException(
                    "User '" + auth.getName() + "' has no linked Staff record. " +
                    "Please ask an administrator to link a staff profile to your account.");
            }

            TriageRequest request = new TriageRequest(
                    appointment.getPatient().getId(),
                    nurse.getId(),
                    appointmentId,
                    chiefComplaint,
                    temperature,
                    bloodPressureSystolic,
                    bloodPressureDiastolic,
                    pulseRate,
                    respiratoryRate,
                    oxygenSaturation,
                    weight,
                    height,
                    bmi,
                    knownAllergies,
                    comorbidities,
                    currentSymptoms,
                    modeOfAmbulation,
                    hasPain,
                    painScore,
                    painLocation,
                    fallRisk,
                    fallScore,
                    infectiousDiseaseRisk,
                    triagePriority,
                    notes,
                    referredDoctorId,
                    referralType
            );

            triageService.create(request);
            redirectAttrs.addFlashAttribute("successMsg",
                    "Triage assessment saved successfully." +
                    (referredDoctorId != null
                            ? " Consultation invoice has been created — patient must pay at the Receptionist."
                            : ""));
        } catch (Exception e) {
            log.error("Error saving triage assessment for appointmentId={}", appointmentId, e);
            redirectAttrs.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
            return "redirect:/nurse/assessment/" + appointmentId;
        }

        return "redirect:/nurse/dashboard";
    }
}
