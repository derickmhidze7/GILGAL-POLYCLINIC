package com.adags.hospital.controller;

import com.adags.hospital.domain.appointment.Appointment;
import com.adags.hospital.domain.medicalrecord.ConsultationStatus;
import com.adags.hospital.domain.medicalrecord.MedicalRecord;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.triage.ModeOfAmbulation;
import com.adags.hospital.domain.triage.TriageAssessment;
import com.adags.hospital.domain.user.AppUser;
import com.adags.hospital.domain.user.Role;
import com.adags.hospital.dto.consultation.ConsultationFormRequest;
import com.adags.hospital.exception.ResourceNotFoundException;
import com.adags.hospital.repository.appointment.AppointmentRepository;
import com.adags.hospital.repository.medicalrecord.MedicalRecordRepository;
import com.adags.hospital.repository.staff.StaffRepository;
import com.adags.hospital.repository.user.UserRepository;
import com.adags.hospital.domain.ward.WardPatientAssignment;
import com.adags.hospital.domain.ward.WardPatientStatus;
import com.adags.hospital.domain.ward.MedicationAdministrationRecord;
import com.adags.hospital.service.consultation.ConsultationService;
import com.adags.hospital.service.lab.LabTechService;
import com.adags.hospital.service.surgery.SurgeryService;
import com.adags.hospital.service.ward.WardNurseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.*;
import com.adags.hospital.domain.surgery.AnesthesiaType;
import com.adags.hospital.domain.surgery.SurgeryItemListType;
import com.adags.hospital.domain.surgery.SurgeryOrder;
import com.adags.hospital.domain.surgery.SurgeryUrgency;
import com.adags.hospital.dto.consultation.SurgeryOrderRequest;
import com.adags.hospital.repository.patient.PatientRepository;
import com.adags.hospital.repository.surgery.SurgeryOrderRepository;
import com.adags.hospital.repository.visit.VisitLabRequestRepository;
import com.adags.hospital.repository.visit.VisitPrescriptionRepository;
import com.adags.hospital.repository.ward.WardPatientAssignmentRepository;

@Slf4j
@Controller
@RequestMapping("/doctor")
@RequiredArgsConstructor
public class DoctorViewController {

    private final ConsultationService           consultationService;
    private final AppointmentRepository         appointmentRepository;
    private final MedicalRecordRepository       medicalRecordRepository;
    private final LabTechService                labTechService;
    private final StaffRepository               staffRepository;
    private final UserRepository                userRepository;
    private final WardNurseService              wardNurseService;
    private final SurgeryService                surgeryService;
    private final PatientRepository             patientRepository;
    private final VisitLabRequestRepository     visitLabRequestRepository;
    private final VisitPrescriptionRepository   visitPrescriptionRepository;
    private final WardPatientAssignmentRepository wardPatientAssignmentRepository;
    private final SurgeryOrderRepository        surgeryOrderRepository;

    @Value("${app.upload.path:uploads}")
    private String uploadBasePath;

    // -----------------------------------------------------------------------
    // Dashboard
    // -----------------------------------------------------------------------

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) {
            return "redirect:/login?error=role";
        }
        List<Appointment> queue = consultationService.getDoctorQueue(doctor.getId());
        Set<java.util.UUID> paidIds = consultationService.getPaidConsultationAppointmentIds(queue);
        model.addAttribute("queue", queue);
        model.addAttribute("paidIds", paidIds);
        model.addAttribute("myPatients", consultationService.getDoctorPatientHistory(doctor.getId()));
        model.addAttribute("doctor", doctor);
        model.addAttribute("activePage", "dashboard");
        return "doctor/dashboard";
    }

    // -----------------------------------------------------------------------
    // Lab Results
    // -----------------------------------------------------------------------

    @GetMapping("/lab-results")
    public String labResults(Model model, Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";

        model.addAttribute("labRequests", labTechService.getDoctorLabResults(doctor.getId()));
        model.addAttribute("doctor", doctor);
        model.addAttribute("activePage", "lab-results");
        return "doctor/lab-results";
    }

    /** Serves the machine-output PDF for a completed visit lab request (from DB). */
    @GetMapping("/visit-request/{id}/result/machine-pdf")
    public ResponseEntity<byte[]> serveVisitMachinePdf(@PathVariable UUID id, Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return ResponseEntity.status(403).build();
        try {
            byte[] data = labTechService.getVisitLabMachinePdfBytes(id);
            if (data == null || data.length == 0) return ResponseEntity.notFound().build();
            String name = labTechService.getVisitLabMachinePdfName(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name + "\"")
                    .body(data);
        } catch (Exception e) {
            log.error("Error serving machine PDF to doctor for visit request {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // -----------------------------------------------------------------------
    // My Patients
    // -----------------------------------------------------------------------

    @GetMapping("/my-patients")
    public String myPatients(Model model, Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";

        model.addAttribute("myPatients", consultationService.getDoctorPatientHistory(doctor.getId()));
        model.addAttribute("doctor", doctor);
        model.addAttribute("activePage", "my-patients");
        return "doctor/my-patients";
    }

    // -----------------------------------------------------------------------
    // Patient Journey — full history for a single patient
    // -----------------------------------------------------------------------

    @GetMapping("/patients/{patientId}/journey")
    public String patientJourney(@PathVariable UUID patientId, Model model, Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";

        var patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId));

        model.addAttribute("patient",       patient);
        model.addAttribute("records",       medicalRecordRepository.findAllByPatientIdOrdered(patientId));
        model.addAttribute("labRequests",   visitLabRequestRepository.findByPatientIdWithDetails(patientId));
        model.addAttribute("prescriptions", visitPrescriptionRepository.findByPatientIdOrdered(patientId));
        model.addAttribute("admissions",    wardPatientAssignmentRepository
                .findByPatientId(patientId, org.springframework.data.domain.Pageable.unpaged()).getContent());
        model.addAttribute("surgeries",     surgeryOrderRepository.findByPatientIdOrderByScheduledDateDesc(patientId));
        model.addAttribute("doctor",        doctor);
        model.addAttribute("activePage",    "my-patients");
        return "doctor/patient-journey";
    }

    @GetMapping("/my-active-patients")
    public String myActivePatients(Model model, Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";
        model.addAttribute("activePatients", medicalRecordRepository.findActivePatientsByDoctor(doctor.getId()));
        model.addAttribute("activePage", "my-active-patients");
        return "doctor/my-active-patients";
    }

    @PostMapping("/medical-records/{recordId}/note")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveDoctorNote(
            @PathVariable UUID recordId,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return ResponseEntity.status(401).body(Map.of("message", "Not authenticated."));
        return medicalRecordRepository.findById(recordId).map(mr -> {
            mr.setDoctorNote(body.get("note"));
            medicalRecordRepository.save(mr);
            return ResponseEntity.ok(Map.<String, Object>of("message", "Note saved."));
        }).orElse(ResponseEntity.notFound().<Map<String, Object>>build());
    }

    // -----------------------------------------------------------------------
    // Admitted Patient Management
    // -----------------------------------------------------------------------

    @GetMapping("/ward-patients")
    public String wardPatients(Model model, Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";

        model.addAttribute("admittedPatients", wardNurseService.getDoctorAdmittedPatients(doctor.getId()));
        model.addAttribute("activePage", "ward-patients");
        return "doctor/ward-patients";
    }

    @GetMapping("/ward-patients/{assignmentId}")
    public String wardPatientDetail(@PathVariable UUID assignmentId, Model model, Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";

        model.addAttribute("detail", wardNurseService.getDoctorPatientDetail(assignmentId));
        model.addAttribute("pendingBills", wardNurseService.getPendingBillsForAssignment(assignmentId));
        model.addAttribute("doctor", doctor);
        model.addAttribute("activePage", "ward-patients");
        return "doctor/ward-patient-detail";
    }

    @PostMapping("/surgeries/{surgeryOrderId}/discharge")
    public String dischargeSurgeryPatient(@PathVariable UUID surgeryOrderId,
                                          Authentication auth,
                                          RedirectAttributes redirectAttrs) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";
        try {
            SurgeryService.SurgeryDetailView surgery = surgeryService.getSurgeryDetail(surgeryOrderId);
            List<WardNurseService.PendingBillItem> pending =
                    wardNurseService.getPendingBillsForPatient(surgery.patientId());
            if (!pending.isEmpty()) {
                redirectAttrs.addFlashAttribute("errorMsg",
                        "Patient cannot be discharged due to pending unpaid bills.");
                redirectAttrs.addFlashAttribute("pendingBills", pending);
                return "redirect:/doctor/surgeries/" + surgeryOrderId;
            }
            surgeryService.updateStatus(surgeryOrderId,
                    com.adags.hospital.domain.surgery.SurgeryStatus.DISCHARGED);
            // Also discharge from ward if patient is currently admitted
            wardNurseService.dischargeActiveWardAdmission(surgery.patientId());
            redirectAttrs.addFlashAttribute("successMsg", "Patient has been discharged successfully.");
        } catch (Exception e) {
            log.error("Error discharging patient from surgery {}: {}", surgeryOrderId, e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Could not discharge patient: " + e.getMessage());
        }
        return "redirect:/doctor/surgeries/" + surgeryOrderId;
    }

    @PostMapping("/ward-patients/{assignmentId}/prescriptions")
    public String addWardPrescription(
            @PathVariable UUID assignmentId,
            @RequestParam String medicationName,
            @RequestParam(required = false) UUID priceItemId,
            @RequestParam(defaultValue = "ORAL") String route,
            @RequestParam(required = false) String instructions,
            @RequestParam(defaultValue = "false") boolean dispenseAsWhole,
            @RequestParam(required = false) Integer dosesPerDay,
            @RequestParam(required = false) Integer quantityPerDose,
            @RequestParam(required = false) Integer numberOfDays,
            @RequestParam(required = false) Integer totalQuantity,
            Authentication auth,
            RedirectAttributes redirectAttrs) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";
        try {
            wardNurseService.addWardPrescription(assignmentId, medicationName, priceItemId,
                    route, instructions, dispenseAsWhole, dosesPerDay, quantityPerDose,
                    numberOfDays, totalQuantity, doctor.getId());
            redirectAttrs.addFlashAttribute("successMsg", "Prescription added.");
        } catch (Exception e) {
            log.error("Error adding ward prescription for assignment {}: {}", assignmentId, e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Could not add prescription: " + e.getMessage());
        }
        return "redirect:/doctor/ward-patients/" + assignmentId;
    }

    @PostMapping("/ward-patients/{assignmentId}/lab-requests")
    public String addWardLabRequest(
            @PathVariable UUID assignmentId,
            @RequestParam UUID priceItemId,
            @RequestParam(defaultValue = "ROUTINE") String urgency,
            @RequestParam(required = false) String clinicalNotes,
            Authentication auth,
            RedirectAttributes redirectAttrs) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";
        try {
            wardNurseService.addWardLabRequest(assignmentId, priceItemId, urgency, clinicalNotes, doctor.getId());
            redirectAttrs.addFlashAttribute("successMsg", "Lab request added and sent to billing.");
        } catch (Exception e) {
            log.error("Error adding ward lab request for assignment {}: {}", assignmentId, e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Could not add lab request: " + e.getMessage());
        }
        return "redirect:/doctor/ward-patients/" + assignmentId + "?tab=lab";
    }

    @PostMapping("/ward-patients/{assignmentId}/discharge")
    public String dischargePatient(@PathVariable UUID assignmentId,
                                   @RequestParam(defaultValue = "") String dischargeNotes,
                                   Authentication auth,
                                   RedirectAttributes redirectAttrs) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";
        try {
            List<WardNurseService.PendingBillItem> pending =
                    wardNurseService.getPendingBillsForAssignment(assignmentId);
            if (!pending.isEmpty()) {
                redirectAttrs.addFlashAttribute("errorMsg",
                        "Patient cannot be discharged due to pending unpaid bills.");
                redirectAttrs.addFlashAttribute("pendingBills", pending);
                return "redirect:/doctor/ward-patients/" + assignmentId;
            }
            wardNurseService.updateStatus(assignmentId, WardPatientStatus.DISCHARGED);
            redirectAttrs.addFlashAttribute("successMsg", "Patient has been successfully discharged.");
            return "redirect:/doctor/ward-patients";
        } catch (Exception e) {
            log.error("Error discharging patient assignment {}: {}", assignmentId, e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Could not discharge patient: " + e.getMessage());
            return "redirect:/doctor/ward-patients/" + assignmentId;
        }
    }

    // -----------------------------------------------------------------------
    // Consultation form — GET
    // -----------------------------------------------------------------------

    @GetMapping("/consultation/{appointmentId}")
    public String consultationForm(@PathVariable UUID appointmentId,
                                   Model model,
                                   Authentication auth,
                                   RedirectAttributes redirectAttrs) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";

        Appointment appointment = appointmentRepository.findByIdWithPatient(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));

        // ---- PAYMENT GUARD ------------------------------------------------
        if (!consultationService.isConsultationFeePaid(appointmentId)) {
            redirectAttrs.addFlashAttribute("errorMsg",
                    appointment.getPatient().getFirstName() + " " + appointment.getPatient().getLastName()
                    + " has not paid the consultation fee. Please ask the patient to settle the fee at reception before proceeding.");
            return "redirect:/doctor/dashboard";
        }
        // -------------------------------------------------------------------

        Optional<TriageAssessment> triage = consultationService.getTriageForAppointment(appointmentId);
        Optional<MedicalRecord>    existing = consultationService.getExistingConsultation(appointmentId);

        // Is this record locked?
        boolean locked = existing
                .map(r -> r.getConsultationStatus() == ConsultationStatus.LOCKED
                       || r.getConsultationStatus() == ConsultationStatus.FINALIZED)
                .orElse(false);

        // Doctors and specialists list for the "Forward patient" dropdown
        List<Staff> doctors = new ArrayList<>(staffRepository.findByStaffRoleAndActiveTrue(Role.DOCTOR));
        doctors.addAll(staffRepository.findByStaffRoleAndActiveTrue(Role.SPECIALIST_DOCTOR));
        doctors.removeIf(s -> s.getId().equals(doctor.getId())); // exclude self

        // Ward nurses for the admission panel
        List<Staff> wardNurses = new ArrayList<>(staffRepository.findByStaffRoleAndActiveTrue(Role.WARD_NURSE));
        wardNurses.addAll(staffRepository.findByStaffRoleAndActiveTrue(Role.NURSE));

        model.addAttribute("appointment", appointment);
        model.addAttribute("patient", appointment.getPatient());
        model.addAttribute("triage", triage.orElse(null));
        model.addAttribute("record", existing.orElse(null));
        model.addAttribute("locked", locked);
        model.addAttribute("doctors", doctors);
        model.addAttribute("wardNurses", wardNurses);
        model.addAttribute("doctor", doctor);
        model.addAttribute("activePage", "consultation");
        model.addAttribute("ambulationValues", ModeOfAmbulation.values());

        // Active ward assignment for this patient (null if not currently admitted)
        java.util.Optional<WardPatientAssignment> wardAssignment =
                wardNurseService.getActiveAssignmentForPatient(appointment.getPatient().getId());
        model.addAttribute("wardAssignment", wardAssignment.orElse(null));

        return "doctor/consultation";
    }

    // -----------------------------------------------------------------------
    // Consultation form — POST (submitted as JSON body)
    // -----------------------------------------------------------------------

    @PostMapping("/consultation/{appointmentId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitConsultation(
            @PathVariable UUID appointmentId,
            @RequestBody ConsultationFormRequest form,
            Authentication auth) {

        Staff doctor = getDoctor(auth);
        if (doctor == null) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "Unauthorized"));
        }

        try {
            MedicalRecord saved = consultationService.saveConsultation(appointmentId, doctor, form);

            // If finalized with ADMITTED, create ward admission record
            if (form.isFinalize() && "ADMITTED".equals(form.getNextStep())) {
                try {
                    wardNurseService.admitPatient(
                            saved.getPatient().getId(),
                            doctor.getId(),
                            form.getNurseId(),
                            form.getWardName(),
                            form.getExpectedAdmissionDays(),
                            "Admitted from consultation by Dr. " + doctor.getFirstName() + " " + doctor.getLastName()
                    );
                } catch (Exception wardEx) {
                    log.warn("Ward admission record creation failed for patient {}: {}",
                             saved.getPatient().getId(), wardEx.getMessage());
                }
            }

            // If finalized with SCHEDULE_SURGERY, create surgery order
            if (form.isFinalize() && "SCHEDULE_SURGERY".equals(form.getNextStep()
            ) && form.getSurgeryOrder() != null) {
                try {
                    surgeryService.createSurgeryOrderFromRecord(saved, doctor, form.getSurgeryOrder());
                } catch (Exception surgEx) {
                    log.warn("Surgery order creation failed for patient {}: {}",
                             saved.getPatient().getId(), surgEx.getMessage());
                }
            }

            String status = saved.getConsultationStatus().name();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "status", status,
                    "message", form.isFinalize()
                            ? "Consultation finalized successfully."
                            : "Draft saved."
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error saving consultation for appointment {}", appointmentId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "Error: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------
    // Surgery — page endpoints
    // -----------------------------------------------------------------------

    @GetMapping("/surgeries")
    public String surgeriesList(Model model, Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";
        model.addAttribute("surgeries", surgeryService.getDoctorSurgeries(doctor.getId()));
        model.addAttribute("doctor", doctor);
        model.addAttribute("activePage", "surgeries");
        return "doctor/surgeries";
    }

    @GetMapping("/surgeries/{surgeryOrderId}")
    public String surgeryDetail(@PathVariable UUID surgeryOrderId, Model model, Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";
        SurgeryService.SurgeryDetailView surgery = surgeryService.getSurgeryDetail(surgeryOrderId);
        model.addAttribute("surgery", surgery);
        model.addAttribute("doctor", doctor);
        model.addAttribute("wardNurses", staffRepository.findByStaffRoleAndActiveTrue(Role.WARD_NURSE));
        model.addAttribute("wardOptions", com.adags.hospital.domain.ward.WardOption.getAll());
        model.addAttribute("alreadyAdmitted", wardNurseService.isPatientActivelyAdmitted(surgery.patientId()));
        model.addAttribute("pendingBills", wardNurseService.getPendingBillsForPatient(surgery.patientId()));
        model.addAttribute("activePage", "surgeries");
        return "doctor/surgery-detail";
    }

    @PostMapping("/surgeries/{surgeryOrderId}/admit")
    public String admitPatientFromSurgery(@PathVariable UUID surgeryOrderId,
                                          @RequestParam UUID nurseId,
                                          @RequestParam String ward,
                                          @RequestParam(required = false) Integer expectedDays,
                                          @RequestParam(required = false) String admissionNotes,
                                          Authentication auth,
                                          RedirectAttributes redirectAttrs) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";
        try {
            SurgeryService.SurgeryDetailView surgery = surgeryService.getSurgeryDetail(surgeryOrderId);
            wardNurseService.admitPatient(surgery.patientId(), doctor.getId(), nurseId, ward, expectedDays, admissionNotes);
            redirectAttrs.addFlashAttribute("successMsg", "Patient admitted to " + ward + " successfully.");
        } catch (Exception e) {
            log.error("Error admitting patient from surgery {}: {}", surgeryOrderId, e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Could not admit patient: " + e.getMessage());
        }
        return "redirect:/doctor/surgeries/" + surgeryOrderId;
    }

    // -----------------------------------------------------------------------
    // Surgery — JSON API
    // -----------------------------------------------------------------------

    @GetMapping("/api/surgery-catalog")
    @ResponseBody
    public ResponseEntity<List<SurgeryService.SurgeryProcedure>> surgeryCatalog(
            @RequestParam(defaultValue = "") String q) {
        return ResponseEntity.ok(surgeryService.searchSurgeryProcedures(q));
    }

    @GetMapping("/api/available-nurses")
    @ResponseBody
    public ResponseEntity<List<SurgeryService.NurseAvailable>> availableNurses() {
        return ResponseEntity.ok(surgeryService.getAvailableNurses());
    }

    @GetMapping("/api/pharmacy-search")
    @ResponseBody
    public ResponseEntity<List<SurgeryService.PharmacyItemSuggestion>> pharmacySearch(
            @RequestParam(defaultValue = "") String q) {
        return ResponseEntity.ok(surgeryService.searchPharmacyItems(q));
    }

    @GetMapping("/api/lab-search")
    @ResponseBody
    public ResponseEntity<List<SurgeryService.LabItemSuggestion>> labSearch(
            @RequestParam(defaultValue = "") String q) {
        return ResponseEntity.ok(surgeryService.searchLabItems(q));
    }

    @PostMapping("/surgeries/{surgeryOrderId}/item")
    public String addSurgeryItem(@PathVariable UUID surgeryOrderId,
                                 @RequestParam String itemType,
                                 @RequestParam String itemName,
                                 @RequestParam(defaultValue = "1") int quantity,
                                 @RequestParam(required = false) BigDecimal price,
                                 @RequestParam(defaultValue = "ORAL") String route,
                                 @RequestParam(required = false) String instructions,
                                 @RequestParam(defaultValue = "false") boolean dispenseAsWhole,
                                 @RequestParam(required = false) Integer dosesPerDay,
                                 @RequestParam(required = false) Integer quantityPerDose,
                                 @RequestParam(required = false) Integer numberOfDays,
                                 Authentication auth,
                                 RedirectAttributes redirectAttrs) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";
        try {
            SurgeryItemListType type = SurgeryItemListType.valueOf(itemType);
            surgeryService.addItem(surgeryOrderId, type, itemName, quantity, price,
                    route, instructions, dispenseAsWhole, dosesPerDay, quantityPerDose, numberOfDays);
            redirectAttrs.addFlashAttribute("successMsg", "Item added successfully.");
        } catch (Exception e) {
            log.error("Error adding surgery item: {}", e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Could not add item: " + e.getMessage());
        }
        String tab = "PRE_OP".equals(itemType) ? "#tabPreop" : "#tabPostopItems";
        return "redirect:/doctor/surgeries/" + surgeryOrderId + tab;
    }

    @PostMapping("/surgeries/{surgeryOrderId}/lab-item")
    public String addSurgeryLabItem(@PathVariable UUID surgeryOrderId,
                                    @RequestParam String itemType,
                                    @RequestParam String itemName,
                                    @RequestParam(required = false) String testCode,
                                    @RequestParam(defaultValue = "1") int quantity,
                                    @RequestParam(required = false) BigDecimal price,
                                    Authentication auth,
                                    RedirectAttributes redirectAttrs) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";
        try {
            SurgeryItemListType type = SurgeryItemListType.valueOf(itemType);
            surgeryService.addLabItem(surgeryOrderId, type, itemName, testCode, quantity, price);
            redirectAttrs.addFlashAttribute("successMsg", "Lab request added successfully.");
        } catch (Exception e) {
            log.error("Error adding surgery lab item: {}", e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Could not add lab request: " + e.getMessage());
        }
        String tab = "PRE_OP".equals(itemType) ? "#tabPreop" : "#tabPostopItems";
        return "redirect:/doctor/surgeries/" + surgeryOrderId + tab;
    }

    @PostMapping("/surgeries/{surgeryOrderId}/postop")
    public String recordPostopCare(@PathVariable UUID surgeryOrderId,
                                   @RequestParam(required = false) String consciousnessLevel,
                                   @RequestParam(required = false) String bloodPressure,
                                   @RequestParam(required = false) Integer pulseRate,
                                   @RequestParam(required = false) String spo2,
                                   @RequestParam(required = false) Integer painScore,
                                   @RequestParam(required = false) String temperature,
                                   @RequestParam(required = false) String recoveryNotes,
                                   @RequestParam(required = false) String nextStep,
                                   Authentication auth,
                                   RedirectAttributes redirectAttrs) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";
        try {
            java.math.BigDecimal spo2Dec = (spo2 != null && !spo2.isBlank())
                    ? new java.math.BigDecimal(spo2) : null;
            java.math.BigDecimal tempDec = (temperature != null && !temperature.isBlank())
                    ? new java.math.BigDecimal(temperature) : null;
            surgeryService.savePostopCare(surgeryOrderId, doctor.getId(),
                    consciousnessLevel, bloodPressure, pulseRate,
                    spo2Dec, painScore, tempDec, recoveryNotes, nextStep);
            redirectAttrs.addFlashAttribute("successMsg", "Post-op care record saved.");
        } catch (Exception e) {
            log.error("Error recording post-op care for surgery {}: {}", surgeryOrderId, e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
        }
        return "redirect:/doctor/surgeries/" + surgeryOrderId + "#tabPostopCare";
    }

    @PostMapping("/surgeries/{surgeryOrderId}/complete")
    public String completeSurgery(@PathVariable UUID surgeryOrderId,
                                  @RequestParam(defaultValue = "") String postopNotes,
                                  Authentication auth,
                                  RedirectAttributes redirectAttrs) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";
        try {
            surgeryService.completeSurgery(surgeryOrderId, postopNotes);
            redirectAttrs.addFlashAttribute("successMsg", "Surgery marked as completed.");
        } catch (Exception e) {
            log.error("Error completing surgery {}: {}", surgeryOrderId, e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Could not complete surgery: " + e.getMessage());
        }
        return "redirect:/doctor/surgeries/" + surgeryOrderId;
    }

    @PostMapping("/surgeries/{surgeryOrderId}/consent-upload")
    public String uploadConsentDocument(@PathVariable UUID surgeryOrderId,
                                        @RequestParam("consentFile") MultipartFile consentFile,
                                        Authentication auth,
                                        RedirectAttributes redirectAttrs) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";
        try {
            surgeryService.uploadConsentDocument(surgeryOrderId, consentFile, uploadBasePath);
            redirectAttrs.addFlashAttribute("successMsg", "Consent document uploaded successfully.");
        } catch (Exception e) {
            log.error("Error uploading consent document for surgery {}: {}", surgeryOrderId, e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Upload failed: " + e.getMessage());
        }
        return "redirect:/doctor/surgeries/" + surgeryOrderId;
    }

    @GetMapping("/surgeries/{surgeryOrderId}/consent-download")
    public ResponseEntity<Resource> downloadConsentDocument(@PathVariable UUID surgeryOrderId,
                                                            Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return ResponseEntity.status(403).build();
        try {
            SurgeryService.SurgeryDetailView surgery = surgeryService.getSurgeryDetail(surgeryOrderId);
            if (surgery.consentDocumentPath() == null) {
                return ResponseEntity.notFound().build();
            }
            Path filePath = Paths.get(uploadBasePath).resolve(surgery.consentDocumentPath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"consent-" + surgeryOrderId + ".pdf\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("Error downloading consent document for surgery {}: {}", surgeryOrderId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // -----------------------------------------------------------------------
    // Send surgery invoice for payment collection
    // -----------------------------------------------------------------------

    @PostMapping("/surgeries/{surgeryOrderId}/send-for-payment")
    public String sendSurgeryForPayment(@PathVariable UUID surgeryOrderId,
                                        Authentication auth,
                                        RedirectAttributes redirectAttrs) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";
        try {
            surgeryService.sendForPayment(surgeryOrderId);
            redirectAttrs.addFlashAttribute("successMsg",
                    "Surgery invoice sent for payment collection at reception.");
        } catch (Exception e) {
            log.error("Error sending surgery {} for payment: {}", surgeryOrderId, e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
        }
        return "redirect:/doctor/surgeries/" + surgeryOrderId;
    }

    // -----------------------------------------------------------------------
    // Create surgery for an already-admitted ward patient
    // -----------------------------------------------------------------------

    @GetMapping("/ward-patients/{assignmentId}/surgery")
    public String showWardPatientSurgeryForm(@PathVariable UUID assignmentId,
                                             Authentication auth,
                                             Model model) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";
        WardPatientAssignment assignment = wardNurseService.getAssignment(assignmentId);
        model.addAttribute("assignment",       assignment);
        model.addAttribute("patient",          assignment.getPatient());
        model.addAttribute("urgencyValues",    SurgeryUrgency.values());
        model.addAttribute("anesthesiaValues", AnesthesiaType.values());
        model.addAttribute("availableNurses",  surgeryService.getAvailableNurses());
        model.addAttribute("procedures",       surgeryService.searchSurgeryProcedures(""));
        model.addAttribute("activePage",       "ward-patients");
        return "doctor/surgery-create";
    }

    @PostMapping("/ward-patients/{assignmentId}/surgery")
    public String createSurgeryFromWardPatient(@PathVariable UUID assignmentId,
                                               @RequestParam(required = false) UUID servicePriceItemId,
                                               @RequestParam String procedureName,
                                               @RequestParam(required = false) String surgeryType,
                                               @RequestParam(required = false) String urgency,
                                               @RequestParam(required = false) String anesthesiaType,
                                               @RequestParam(required = false) String scheduledDate,
                                               @RequestParam(required = false) String operatingTheater,
                                               @RequestParam(required = false) Integer estimatedDurationMinutes,
                                               @RequestParam(required = false) BigDecimal price,
                                               @RequestParam(required = false) List<UUID> nurseIds,
                                               @RequestParam(required = false) String preopNotes,
                                               @RequestParam(defaultValue = "false") boolean consentObtained,
                                               Authentication auth,
                                               RedirectAttributes redirectAttrs) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";
        try {
            WardPatientAssignment assignment = wardNurseService.getAssignment(assignmentId);
            SurgeryOrderRequest req = new SurgeryOrderRequest();
            req.setServicePriceItemId(servicePriceItemId);
            req.setProcedureName(procedureName);
            req.setSurgeryType(surgeryType);
            req.setUrgency(urgency);
            req.setAnesthesiaType(anesthesiaType);
            if (scheduledDate != null && !scheduledDate.isBlank()) {
                req.setScheduledDate(LocalDateTime.parse(scheduledDate,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")));
            }
            req.setOperatingTheater(operatingTheater);
            req.setEstimatedDurationMinutes(estimatedDurationMinutes);
            req.setPrice(price);
            req.setNurseIds(nurseIds);
            req.setPreopNotes(preopNotes);
            req.setConsentObtained(consentObtained);
            SurgeryOrder created = surgeryService.createSurgeryOrderFromWardAdmission(assignment, req);
            redirectAttrs.addFlashAttribute("successMsg",
                    "Surgery order created. Patient remains admitted to the ward.");
            return "redirect:/doctor/surgeries/" + created.getId();
        } catch (Exception e) {
            log.error("Error creating surgery from ward assignment {}: {}", assignmentId, e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Could not create surgery: " + e.getMessage());
            return "redirect:/doctor/ward-patients/" + assignmentId;
        }
    }

    // -----------------------------------------------------------------------
    // Ward medication order from doctor (admitted patient)
    // -----------------------------------------------------------------------

    @PostMapping("/consultation/{appointmentId}/ward-medication")
    public String addWardMedication(@PathVariable UUID appointmentId,
                                    @RequestParam UUID assignmentId,
                                    @RequestParam String medicationName,
                                    @RequestParam(required = false) String dose,
                                    @RequestParam(required = false) String route,
                                    @RequestParam(required = false) String scheduledTime,
                                    @RequestParam(required = false) String instructions,
                                    Authentication auth,
                                    RedirectAttributes redirectAttrs) {
        try {
            Staff doctor = getDoctor(auth);
            LocalDateTime scheduled;
            if (scheduledTime != null && !scheduledTime.isBlank()) {
                try {
                    scheduled = LocalDateTime.parse(scheduledTime);
                } catch (DateTimeParseException ex) {
                    // datetime-local input gives "yyyy-MM-ddTHH:mm" (no seconds)
                    scheduled = LocalDateTime.parse(scheduledTime,
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
                }
            } else {
                scheduled = LocalDateTime.now().plusHours(1);
            }
            MedicationAdministrationRecord record = MedicationAdministrationRecord.builder()
                    .medicationName(medicationName)
                    .scheduledTime(scheduled)
                    .doseGiven(dose)
                    .route(route)
                    .wasGiven(false)
                    .remarks("Doctor order — " + (instructions != null ? instructions : ""))
                    .administeredBy(doctor)
                    .build();
            wardNurseService.recordMedication(assignmentId, record, null);
            redirectAttrs.addFlashAttribute("successMsg",
                    "Medication order '" + medicationName + "' sent to the ward nurse.");
        } catch (Exception e) {
            log.error("Error adding ward medication order for appointment {}: {}", appointmentId, e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
        }
        return "redirect:/doctor/consultation/" + appointmentId;
    }

    // -----------------------------------------------------------------------
    // Prescriptions page
    // -----------------------------------------------------------------------

    @GetMapping("/prescriptions")
    public String prescriptionsPage(Model model, Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";
        model.addAttribute("doctor", doctor);
        model.addAttribute("activePage", "prescriptions");
        return "doctor/prescriptions";
    }

    // -----------------------------------------------------------------------
    // Lab Requests page
    // -----------------------------------------------------------------------

    @GetMapping("/lab-requests")
    public String labRequestsPage(Model model, Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";
        model.addAttribute("doctor", doctor);
        model.addAttribute("activePage", "lab-requests");
        return "doctor/lab-requests";
    }

    // -----------------------------------------------------------------------
    // Hospital Catalogue
    // -----------------------------------------------------------------------

    @GetMapping("/hospital-catalogue")
    public String hospitalCataloguePage(Model model, Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return "redirect:/login?error=role";
        model.addAttribute("doctor", doctor);
        model.addAttribute("activePage", "hospital-catalogue");
        return "doctor/hospital-catalogue";
    }

    // -----------------------------------------------------------------------
    // Ward lab request from doctor (admitted patient)
    // -----------------------------------------------------------------------

    @PostMapping("/consultation/{appointmentId}/ward-lab")
    public String addWardLabRequest(@PathVariable UUID appointmentId,
                                    @RequestParam String testName,
                                    @RequestParam(required = false) UUID itemId,
                                    @RequestParam(defaultValue = "ROUTINE") String urgency,
                                    @RequestParam(required = false) String instructions,
                                    Authentication auth,
                                    RedirectAttributes redirectAttrs) {
        try {
            Staff doctor = getDoctor(auth);
            consultationService.addLabRequest(
                    /* recordId via appointmentId */ getRecordIdForAppointment(appointmentId),
                    doctor, itemId, testName, urgency, instructions);
            redirectAttrs.addFlashAttribute("successMsg",
                    "Lab request '" + testName + "' queued for the lab technician.");
        } catch (Exception e) {
            log.error("Error adding ward lab request for appointment {}: {}", appointmentId, e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
        }
        return "redirect:/doctor/consultation/" + appointmentId;
    }

    private UUID getRecordIdForAppointment(UUID appointmentId) {
        return consultationService.getExistingConsultation(appointmentId)
                .map(com.adags.hospital.domain.common.BaseEntity::getId)
                .orElseThrow(() -> new com.adags.hospital.exception.ResourceNotFoundException(
                        "MedicalRecord", "appointmentId", appointmentId));
    }

    // -----------------------------------------------------------------------
    // Web error fallback — prevents GlobalExceptionHandler (JSON) from
    // intercepting page-load failures; redirects with a user-friendly message.
    // -----------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public String handlePageError(Exception ex,
                                  jakarta.servlet.http.HttpServletRequest request,
                                  RedirectAttributes redirectAttrs) {
        log.error("Error rendering doctor page [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        redirectAttrs.addFlashAttribute("errorMsg",
                "An error occurred loading the page: " + ex.getMessage());
        return "redirect:/doctor/dashboard";
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private Staff getDoctor(Authentication auth) {
        if (auth == null) return null;
        AppUser user = userRepository.findByUsernameWithStaff(auth.getName()).orElse(null);
        return user != null ? user.getStaff() : null;
    }
}
