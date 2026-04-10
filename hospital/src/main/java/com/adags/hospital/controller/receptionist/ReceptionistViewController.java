package com.adags.hospital.controller.receptionist;

import com.adags.hospital.domain.appointment.AppointmentStatus;
import com.adags.hospital.domain.appointment.Appointment;
import com.adags.hospital.domain.appointment.AppointmentType;
import com.adags.hospital.domain.billing.Invoice;
import com.adags.hospital.domain.billing.InvoiceStatus;
import com.adags.hospital.domain.billing.PaymentMethod;
import com.adags.hospital.domain.common.Gender;
import com.adags.hospital.domain.medicalrecord.MedicalRecord;
import com.adags.hospital.domain.patient.MaritalStatus;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.triage.TriageAssessment;
import com.adags.hospital.domain.user.AppUser;
import com.adags.hospital.domain.user.Role;
import com.adags.hospital.repository.user.UserRepository;
import com.adags.hospital.dto.appointment.AppointmentRequest;
import com.adags.hospital.dto.billing.InvoiceRequest;
import com.adags.hospital.dto.patient.PatientRequest;
import com.adags.hospital.dto.patient.PatientResponse;
import com.adags.hospital.dto.appointment.AppointmentResponse;
import com.adags.hospital.dto.triage.TriageResponse;
import com.adags.hospital.repository.billing.InvoiceRepository;
import com.adags.hospital.repository.appointment.AppointmentRepository;
import com.adags.hospital.repository.medicalrecord.MedicalRecordRepository;
import com.adags.hospital.repository.staff.StaffRepository;
import com.adags.hospital.repository.triage.TriageRepository;
import com.adags.hospital.repository.ward.WardPatientAssignmentRepository;
import com.adags.hospital.repository.surgery.SurgeryOrderRepository;
import com.adags.hospital.repository.pricing.ServicePriceItemRepository;
import com.adags.hospital.domain.ward.WardPatientAssignment;
import com.adags.hospital.domain.surgery.SurgeryOrder;
import com.adags.hospital.domain.surgery.SurgeryStatus;
import com.adags.hospital.domain.ward.WardPatientStatus;
import com.adags.hospital.domain.billing.LineItemCategory;
import com.adags.hospital.domain.pricing.ServicePriceItem;
import com.adags.hospital.service.appointment.AppointmentService;
import com.adags.hospital.service.billing.BillingService;
import com.adags.hospital.service.patient.PatientService;
import com.adags.hospital.service.triage.TriageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/receptionist")
@RequiredArgsConstructor
public class ReceptionistViewController {

    /** Carries specialist-forwarding info for a single appointment row in the receptionist view. */
    public record SpecialistForwardInfo(Staff forwardedDoctor, boolean invoicePaid) {}

    private final PatientService patientService;
    private final AppointmentService appointmentService;
    private final BillingService billingService;
    private final TriageService triageService;
    private final StaffRepository staffRepository;
    private final InvoiceRepository invoiceRepository;
    private final AppointmentRepository appointmentRepository;
    private final TriageRepository triageRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final WardPatientAssignmentRepository wardAssignmentRepository;
    private final SurgeryOrderRepository           surgeryOrderRepository;
    private final ServicePriceItemRepository servicePriceItemRepository;
    private final UserRepository userRepository;

    // -----------------------------------------------------------------------
    // Dashboard
    // -----------------------------------------------------------------------

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        long totalPatients = patientService.getAll(PageRequest.of(0, 1)).getTotalElements();
        List<AppointmentResponse> recentAppointments = appointmentService
                .getAll(PageRequest.of(0, 8, Sort.by("scheduledDateTime").descending()))
                .getContent();
        long totalInvoices = billingService.getAll(PageRequest.of(0, 1)).getTotalElements();

        model.addAttribute("totalPatients", totalPatients);
        model.addAttribute("recentAppointments", recentAppointments);
        model.addAttribute("totalInvoices", totalInvoices);
        model.addAttribute("activePage", "dashboard");
        return "receptionist/dashboard";
    }

    // -----------------------------------------------------------------------
    // Patients — list / search
    // -----------------------------------------------------------------------

    @GetMapping("/patients")
    public String patients() {
        return "redirect:/receptionist/patients/register";
    }

    @GetMapping("/patients/register")
    public String registerPatients(@RequestParam(required = false) String search,
                                   @RequestParam(defaultValue = "0") int page,
                                   Model model) {
        // Build set of patient IDs currently receiving any active service
        Set<UUID> busyIds = new HashSet<>();
        appointmentRepository.findActiveTriageQueue()
                .forEach(a -> busyIds.add(a.getPatient().getId()));
        appointmentRepository.findAssessedAwaitingDoctorQueue()
                .forEach(a -> busyIds.add(a.getPatient().getId()));
        appointmentRepository.findAllActiveConsultationAppointments()
                .forEach(a -> busyIds.add(a.getPatient().getId()));
        wardAssignmentRepository.findPatientIdsWithLatestStatusIn(
                List.of(WardPatientStatus.ADMITTED, WardPatientStatus.STABLE, WardPatientStatus.CRITICAL))
                .forEach(busyIds::add);
        surgeryOrderRepository.findByStatusOrderByScheduledDateAsc(SurgeryStatus.SCHEDULED)
                .forEach(s -> busyIds.add(s.getPatient().getId()));
        surgeryOrderRepository.findByStatusOrderByScheduledDateAsc(SurgeryStatus.IN_PROGRESS)
                .forEach(s -> busyIds.add(s.getPatient().getId()));

        if (search != null && !search.isBlank()) {
            var results = patientService.searchAll(search, PageRequest.of(page, 20));
            model.addAttribute("searchResults", results.getContent());
            model.addAttribute("currentPage",  results.getNumber());
            model.addAttribute("totalPages",   results.getTotalPages());
            model.addAttribute("totalPatients", results.getTotalElements());
        } else {
            model.addAttribute("idlePatients", patientService.getIdlePatients(busyIds));
        }

        model.addAttribute("busyPatientIds",  busyIds);
        model.addAttribute("genders",         Gender.values());
        model.addAttribute("maritalStatuses", MaritalStatus.values());
        model.addAttribute("search",          search);
        model.addAttribute("activePage",      "patients");
        model.addAttribute("activeSubPage",   "register");
        return "receptionist/patients-register";
    }

    @GetMapping("/patients/all")
    public String allPatients(@RequestParam(defaultValue = "0") int page, Model model) {
        var patients = patientService.getAllIncludingDischarged(PageRequest.of(page, 25, Sort.by("registrationDate").descending()));
        model.addAttribute("patients",     patients.getContent());
        model.addAttribute("currentPage",  patients.getNumber());
        model.addAttribute("totalPages",   patients.getTotalPages());
        model.addAttribute("totalPatients", patients.getTotalElements());
        model.addAttribute("activePage",   "patients");
        model.addAttribute("activeSubPage","all");
        return "receptionist/patients-all";
    }

    @PostMapping("/patients")
    public String registerPatient(
            @RequestParam String firstName,
            @RequestParam(required = false) String middleName,
            @RequestParam String lastName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateOfBirth,
            @RequestParam Gender gender,
            @RequestParam(required = false) MaritalStatus maritalStatus,
            @RequestParam(required = false) String nationalId,
            @RequestParam String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String occupation,
            @RequestParam String nextOfKinFullName,
            @RequestParam String nextOfKinRelationship,
            @RequestParam String nextOfKinPhone,
            @RequestParam(required = false) String currentStreet,
            @RequestParam(required = false) String currentCity,
            @RequestParam(required = false) String permStreet,
            @RequestParam(required = false) String permCity,
            @RequestParam(required = false) String insuranceProvider,
            @RequestParam(required = false) String insurancePolicyNumber,
            @RequestParam(required = false) String insuranceMemberNumber,
            RedirectAttributes redirectAttrs) {
        if (nextOfKinFullName == null || nextOfKinFullName.isBlank()) {
            redirectAttrs.addFlashAttribute("errorMsg", "Next of kin full name is required.");
            redirectAttrs.addFlashAttribute("openRegisterModal", true);
            return "redirect:/receptionist/patients/register";
        }
        if (nextOfKinRelationship == null || nextOfKinRelationship.isBlank()) {
            redirectAttrs.addFlashAttribute("errorMsg", "Next of kin relationship is required.");
            redirectAttrs.addFlashAttribute("openRegisterModal", true);
            return "redirect:/receptionist/patients/register";
        }
        if (nextOfKinPhone == null || nextOfKinPhone.isBlank()) {
            redirectAttrs.addFlashAttribute("errorMsg", "Next of kin phone is required.");
            redirectAttrs.addFlashAttribute("openRegisterModal", true);
            return "redirect:/receptionist/patients/register";
        }
        try {
            PatientRequest request = new PatientRequest(
                    firstName,
                    (middleName != null && !middleName.isBlank()) ? middleName : null,
                    lastName, dateOfBirth, gender,
                    maritalStatus,
                    (nationalId != null && !nationalId.isBlank()) ? nationalId : null,
                    phone,
                    (email != null && !email.isBlank()) ? email : null,
                    (occupation != null && !occupation.isBlank()) ? occupation : null,
                    nextOfKinFullName.strip(),
                    nextOfKinRelationship.strip(),
                    nextOfKinPhone.strip(),
                    (currentStreet != null && !currentStreet.isBlank()) ? currentStreet : null,
                    (currentCity != null && !currentCity.isBlank()) ? currentCity : null,
                    (permStreet != null && !permStreet.isBlank()) ? permStreet : null,
                    (permCity != null && !permCity.isBlank()) ? permCity : null,
                    (insuranceProvider != null && !insuranceProvider.isBlank()) ? insuranceProvider : null,
                    (insurancePolicyNumber != null && !insurancePolicyNumber.isBlank()) ? insurancePolicyNumber : null,
                    (insuranceMemberNumber != null && !insuranceMemberNumber.isBlank()) ? insuranceMemberNumber : null
            );
            patientService.create(request);
            redirectAttrs.addFlashAttribute("successMsg", "Patient registered successfully.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMsg", e.getMessage());
            redirectAttrs.addFlashAttribute("openRegisterModal", true);
        }
        return "redirect:/receptionist/patients/register";
    }

    // Patient profile
    @GetMapping("/patients/{id}")
    public String patientProfile(@PathVariable UUID id, Model model) {
        PatientResponse patient = patientService.getById(id);
        List<AppointmentResponse> appointments = appointmentService
                .getByPatient(id, PageRequest.of(0, 20, Sort.by("scheduledDateTime").descending()))
                .getContent();
        List<Invoice> invoices = invoiceRepository
                .findByPatientIdFetchPatient(id, PageRequest.of(0, 20, Sort.by("createdAt").descending()));
        List<Staff> doctors = new ArrayList<>(staffRepository.findByStaffRoleAndActiveTrue(Role.DOCTOR));
        doctors.addAll(staffRepository.findByStaffRoleAndActiveTrue(Role.SPECIALIST_DOCTOR));

        model.addAttribute("patient", patient);
        model.addAttribute("appointments", appointments);
        model.addAttribute("invoices", invoices);
        model.addAttribute("doctors", doctors);
        model.addAttribute("appointmentTypes", AppointmentType.values());
        model.addAttribute("paymentMethods", PaymentMethod.values());
        List<TriageResponse> triageHistory = Collections.emptyList();
        try {
            triageHistory = triageService
                    .getByPatient(id, PageRequest.of(0, 20, Sort.by("assessmentDateTime").descending()))
                    .getContent();
        } catch (Exception e) {
            log.warn("Could not load triage history for patient {}: {}", id, e.getMessage(), e);
        }
        model.addAttribute("triageHistory", triageHistory);
        model.addAttribute("activePage", "patients");
        return "receptionist/patient-profile";
    }

    // -----------------------------------------------------------------------
    // Dismiss patient (deactivate — removes from list)
    // -----------------------------------------------------------------------

    @PostMapping("/patients/{id}/dismiss")
    public String dismissPatient(@PathVariable UUID id, RedirectAttributes redirectAttrs) {
        try {
            patientService.deactivate(id);
            redirectAttrs.addFlashAttribute("successMsg", "Patient has been discharged and removed from the active list.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMsg", "Could not dismiss patient: " + e.getMessage());
        }
        return "redirect:/receptionist/patients/register";
    }

    // -----------------------------------------------------------------------
    // Send to Triage — one-click, no form required
    // Creates a TRIAGE-type appointment (status SCHEDULED, time = now).
    // The triage nurse picks it up and fills clinical details from their portal.
    // -----------------------------------------------------------------------

    @PostMapping("/patients/{id}/send-to-triage")
    public String sendToTriage(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "patients") String returnTo,
            RedirectAttributes redirectAttrs) {
        try {
            // Check whether there is already an open TRIAGE appointment for this patient
            List<AppointmentResponse> existing = appointmentService
                    .getByPatient(id, PageRequest.of(0, 50, Sort.by("scheduledDateTime").descending()))
                    .getContent();
            boolean alreadyQueued = existing.stream().anyMatch(a ->
                    a.appointmentType() == AppointmentType.TRIAGE &&
                    (a.status() == AppointmentStatus.SCHEDULED ||
                     a.status() == AppointmentStatus.CONFIRMED ||
                     a.status() == AppointmentStatus.IN_PROGRESS));
            if (alreadyQueued) {
                redirectAttrs.addFlashAttribute("errorMsg",
                        "Patient is already in the triage queue.");
            } else {
                AppointmentRequest req = new AppointmentRequest(
                        id, null,
                        java.time.LocalDateTime.now(),
                        AppointmentType.TRIAGE,
                        AppointmentStatus.SCHEDULED,
                        "Sent to triage by receptionist");
                appointmentService.create(req);
                redirectAttrs.addFlashAttribute("successMsg",
                        "Patient has been added to the triage queue.");
            }
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMsg", e.getMessage());
        }
        if ("profile".equals(returnTo)) {
            return "redirect:/receptionist/patients/" + id;
        }
        return "redirect:/receptionist/patients/register";
    }

    // -----------------------------------------------------------------------
    // Appointments
    // -----------------------------------------------------------------------

    @GetMapping("/appointments")
    public String appointments(@RequestParam(defaultValue = "0") int page, Model model) {
        var apptPage = appointmentService
                .getAll(PageRequest.of(page, 20, Sort.by("scheduledDateTime").descending()));
        List<AppointmentResponse> appointments = apptPage.getContent();
        List<PatientResponse> patients = patientService
                .getAll(PageRequest.of(0, 200, Sort.by("lastName")))
                .getContent();
        List<Staff> doctors = new ArrayList<>(staffRepository.findByStaffRoleAndActiveTrue(Role.DOCTOR));
        doctors.addAll(staffRepository.findByStaffRoleAndActiveTrue(Role.SPECIALIST_DOCTOR));

        // Build forward map: which appointments have a specialist forwarding set?
        Map<UUID, SpecialistForwardInfo> forwardMap = new HashMap<>();
        List<UUID> apptIds = appointments.stream().map(AppointmentResponse::id).collect(Collectors.toList());
        if (!apptIds.isEmpty()) {
            // 1. Triage-level referrals (nurse sets referred_doctor_id on triage_assessment)
            triageRepository.findByAppointmentIdInWithReferral(apptIds).forEach(t -> {
                boolean paid = t.getConsultationInvoice() != null
                        && t.getConsultationInvoice().getStatus() == InvoiceStatus.PAID;
                forwardMap.put(t.getAppointment().getId(),
                        new SpecialistForwardInfo(t.getReferredDoctor(), paid));
            });
            // 2. Doctor-level forwarding (medical_record.forwarded_to_doctor_id)
            medicalRecordRepository.findByAppointmentIdInWithForwardedDoctor(apptIds).forEach(m -> {
                UUID apptId = m.getAppointment().getId();
                if (!forwardMap.containsKey(apptId)) {
                    boolean paid = invoiceRepository.findByMedicalRecordId(m.getId())
                            .stream().anyMatch(inv -> inv.getStatus() == InvoiceStatus.PAID);
                    forwardMap.put(apptId, new SpecialistForwardInfo(m.getForwardedToDoctor(), paid));
                }
            });
        }

        model.addAttribute("appointments", appointments);
        model.addAttribute("patients", patients);
        model.addAttribute("doctors", doctors);
        model.addAttribute("appointmentTypes", AppointmentType.values());
        model.addAttribute("forwardMap", forwardMap);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", apptPage.getTotalPages());
        model.addAttribute("totalAppointments", apptPage.getTotalElements());
        model.addAttribute("activePage", "appointments");
        return "receptionist/appointments";
    }

    /** Returns a doctor's booked appointments for a given date — used by the receptionist's
     *  'Schedule Specialist' modal to display the doctor's existing schedule. */
    @GetMapping("/api/doctors/{doctorId}/schedule")
    @ResponseBody
    public List<Map<String, Object>> getDoctorSchedule(
            @PathVariable UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end   = date.atTime(LocalTime.MAX);
        return appointmentRepository
                .findByDoctorIdAndScheduledDateTimeBetween(doctorId, start, end)
                .stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .sorted(java.util.Comparator.comparing(Appointment::getScheduledDateTime))
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("time", a.getScheduledDateTime().toLocalTime().toString());
                    m.put("patient", a.getPatient().getFirstName() + " " + a.getPatient().getLastName());
                    m.put("type", a.getAppointmentType().name());
                    m.put("status", a.getStatus().name());
                    return m;
                })
                .collect(Collectors.toList());
    }

    /** Schedule a specialist appointment after verifying the consultation invoice is PAID. */
    @PostMapping("/appointments/schedule-specialist")
    public String scheduleSpecialist(
            @RequestParam UUID patientId,
            @RequestParam UUID doctorId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime scheduledDateTime,
            @RequestParam(required = false) String notes,
            @RequestParam UUID sourceAppointmentId,
            RedirectAttributes redirectAttrs) {
        try {
            // Gate: verify the consultation invoice is PAID
            boolean invoicePaid = false;

            // Check triage-level referral invoice first
            java.util.Optional<TriageAssessment> triageOpt =
                    triageRepository.findByAppointmentId(sourceAppointmentId);
            if (triageOpt.isPresent() && triageOpt.get().getConsultationInvoice() != null) {
                invoicePaid = triageOpt.get().getConsultationInvoice().getStatus() == InvoiceStatus.PAID;
            } else {
                // Fall back to medical record invoices
                java.util.Optional<MedicalRecord> mrOpt =
                        medicalRecordRepository.findByAppointmentId(sourceAppointmentId);
                if (mrOpt.isPresent()) {
                    invoicePaid = invoiceRepository.findByMedicalRecordId(mrOpt.get().getId())
                            .stream().anyMatch(inv -> inv.getStatus() == InvoiceStatus.PAID);
                }
            }

            if (!invoicePaid) {
                redirectAttrs.addFlashAttribute("errorMsg",
                        "Cannot schedule specialist appointment: the consultation invoice has not been paid.");
                return "redirect:/receptionist/appointments";
            }

            AppointmentRequest request = new AppointmentRequest(
                    patientId, doctorId, scheduledDateTime,
                    AppointmentType.SPECIALIST, AppointmentStatus.SCHEDULED,
                    notes != null && !notes.isBlank() ? notes : "Specialist appointment");
            appointmentService.create(request);
            redirectAttrs.addFlashAttribute("successMsg", "Specialist appointment scheduled successfully.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMsg", "Could not schedule specialist appointment: " + e.getMessage());
        }
        return "redirect:/receptionist/appointments";
    }

    /** Reschedule an existing specialist appointment. */
    @PostMapping("/appointments/{id}/reschedule")
    public String rescheduleAppointment(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime scheduledDateTime,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttrs) {
        try {
            Appointment appt = appointmentRepository.findById(id)
                    .orElseThrow(() -> new com.adags.hospital.exception.ResourceNotFoundException(
                            "Appointment", "id", id));
            appt.setScheduledDateTime(scheduledDateTime);
            if (notes != null && !notes.isBlank()) {
                appt.setNotes(notes);
            }
            appointmentRepository.save(appt);
            redirectAttrs.addFlashAttribute("successMsg", "Appointment rescheduled successfully.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMsg", "Could not reschedule appointment: " + e.getMessage());
        }
        return "redirect:/receptionist/appointments";
    }

    @PostMapping("/appointments")
    public String bookAppointment(
            @RequestParam UUID patientId,
            @RequestParam(required = false) UUID doctorId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime scheduledDateTime,
            @RequestParam AppointmentType appointmentType,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttrs) {
        try {
            AppointmentRequest request = new AppointmentRequest(
                    patientId, doctorId, scheduledDateTime, appointmentType,
                    AppointmentStatus.SCHEDULED, notes);
            appointmentService.create(request);
            redirectAttrs.addFlashAttribute("successMsg", "Appointment booked successfully.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/receptionist/appointments";
    }

    @PostMapping("/appointments/{id}/cancel")
    public String cancelAppointment(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            RedirectAttributes redirectAttrs) {
        try {
            appointmentService.updateStatus(id, AppointmentStatus.CANCELLED, reason);
            redirectAttrs.addFlashAttribute("successMsg", "Appointment cancelled.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/receptionist/appointments";
    }

    // -----------------------------------------------------------------------
    // Billing
    // -----------------------------------------------------------------------

    @GetMapping("/billing")
    public String billing(@RequestParam(defaultValue = "0") int page, Model model) {
        var invoicePage = billingService.getAllWithDetails(
                PageRequest.of(page, 20, Sort.by("createdAt").descending()));
        List<Invoice> invoices = invoicePage.getContent();
        List<PatientResponse> patients = patientService
                .getAll(PageRequest.of(0, 200, Sort.by("lastName")))
                .getContent();

        // Build outstanding balance map using an aggregate query to avoid lazy-load issues
        List<UUID> invoiceIds = invoices.stream().map(Invoice::getId).toList();
        java.util.Map<UUID, BigDecimal> paidMap = new java.util.HashMap<>();
        if (!invoiceIds.isEmpty()) {
            invoiceRepository.sumPaymentsByInvoiceIds(invoiceIds).forEach(row -> {
                UUID invId = (UUID) row[0];
                BigDecimal sum = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
                paidMap.put(invId, sum);
            });
        }
        java.util.Map<UUID, BigDecimal> outstandingMap = new java.util.HashMap<>();
        for (Invoice inv : invoices) {
            BigDecimal paid = paidMap.getOrDefault(inv.getId(), BigDecimal.ZERO);
            BigDecimal outstanding = inv.getTotalAmount() != null
                    ? inv.getTotalAmount().subtract(paid).max(BigDecimal.ZERO)
                    : BigDecimal.ZERO;
            outstandingMap.put(inv.getId(), outstanding);
        }

        model.addAttribute("invoices", invoices);
        model.addAttribute("patients", patients);
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("outstandingMap", outstandingMap);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", invoicePage.getTotalPages());
        model.addAttribute("totalInvoiceCount", invoicePage.getTotalElements());
        model.addAttribute("activePage", "billing");
        return "receptionist/billing";
    }

    @GetMapping("/api/price-catalogue/search")
    @ResponseBody
    public List<Map<String, Object>> searchPriceCatalogue(
            @RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) return List.of();
        return servicePriceItemRepository
                .findByProductNameContainingIgnoreCase(q)
                .stream()
                .limit(20)
                .map(item -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", item.getId().toString());
                    m.put("name", item.getProductName());
                    m.put("type", item.getType());
                    m.put("classification", item.getClassification() != null ? item.getClassification() : "");
                    m.put("price", item.getPrice());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @PostMapping("/billing")
    public String createInvoice(
            @RequestParam UUID patientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
            @RequestParam(required = false) String notes,
            @RequestParam(name = "itemDesc", required = false) List<String> itemDescs,
            @RequestParam(name = "itemType", required = false) List<String> itemTypes,
            @RequestParam(name = "itemQty", required = false) List<Integer> itemQtys,
            @RequestParam(name = "itemPrice", required = false) List<BigDecimal> itemPrices,
            RedirectAttributes redirectAttrs) {
        try {
            InvoiceRequest request = new InvoiceRequest(patientId, null, dueDate, notes);
            Invoice invoice = billingService.createInvoice(request);
            if (itemDescs != null) {
                for (int i = 0; i < itemDescs.size(); i++) {
                    String desc = itemDescs.get(i);
                    if (desc == null || desc.isBlank()) continue;
                    String typeStr = (itemTypes != null && i < itemTypes.size()) ? itemTypes.get(i) : "OTHER";
                    int qty = (itemQtys != null && i < itemQtys.size() && itemQtys.get(i) != null) ? itemQtys.get(i) : 1;
                    BigDecimal price = (itemPrices != null && i < itemPrices.size() && itemPrices.get(i) != null) ? itemPrices.get(i) : BigDecimal.ZERO;
                    billingService.addLineItemToInvoice(invoice.getId(), desc, mapTypeToCategory(typeStr), qty, price);
                }
            }
            billingService.issueInvoice(invoice.getId());
            redirectAttrs.addFlashAttribute("successMsg", "Invoice created and issued.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/receptionist/billing";
    }

    private LineItemCategory mapTypeToCategory(String type) {
        if (type == null) return LineItemCategory.CONSULTATION;
        return switch (type.toUpperCase()) {
            case "PHARMACY" -> LineItemCategory.PHARMACY;
            case "LABORATORY", "LAB" -> LineItemCategory.LAB;
            case "SURGERY", "PROCEDURE" -> LineItemCategory.PROCEDURE;
            case "WARD", "BED" -> LineItemCategory.BED;
            default -> LineItemCategory.CONSULTATION;
        };
    }

    @PostMapping("/billing/{id}/payment")
    public String recordPayment(
            @PathVariable UUID id,
            @RequestParam BigDecimal amount,
            @RequestParam PaymentMethod paymentMethod,
            @RequestParam(required = false) String reference,
            RedirectAttributes redirectAttrs) {
        try {
            billingService.recordPayment(id, amount, paymentMethod, reference);
            redirectAttrs.addFlashAttribute("successMsg", "Payment recorded successfully.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/receptionist/billing";
    }

    @PostMapping("/billing/{id}/delete")
    public String deleteVoidedInvoice(
            @PathVariable UUID id,
            RedirectAttributes redirectAttrs) {
        try {
            billingService.deleteVoidedInvoice(id);
            redirectAttrs.addFlashAttribute("successMsg", "Voided invoice removed successfully.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/receptionist/billing";
    }

    @PostMapping("/billing/{id}/cancel")
    public String requestCancellation(
            @PathVariable UUID id,
            @RequestParam String reason,
            Authentication auth,
            RedirectAttributes redirectAttrs) {
        try {
            AppUser currentUser = userRepository.findByUsername(auth.getName())
                    .orElseThrow(() -> new com.adags.hospital.exception.ResourceNotFoundException(
                            "AppUser", "username", auth.getName()));
            billingService.requestCancellation(id, reason, currentUser);
            redirectAttrs.addFlashAttribute("successMsg",
                    "Cancellation request submitted. Awaiting admin approval.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/receptionist/billing";
    }
}
