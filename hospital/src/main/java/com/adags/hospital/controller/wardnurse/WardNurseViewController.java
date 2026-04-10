package com.adags.hospital.controller.wardnurse;

import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.surgery.AnesthesiaType;
import com.adags.hospital.domain.surgery.SurgeryOrder;
import com.adags.hospital.domain.surgery.SurgeryUrgency;
import com.adags.hospital.domain.user.AppUser;
import com.adags.hospital.domain.user.Role;
import com.adags.hospital.domain.ward.*;
import com.adags.hospital.domain.ward.WardOption;
import com.adags.hospital.dto.consultation.SurgeryOrderRequest;
import com.adags.hospital.repository.staff.StaffRepository;
import com.adags.hospital.repository.user.UserRepository;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/ward-nurse")
@RequiredArgsConstructor
public class WardNurseViewController {

    private final WardNurseService wardNurseService;
    private final UserRepository   userRepository;
    private final StaffRepository  staffRepository;
    private final SurgeryService   surgeryService;

    @Value("${app.upload.path:uploads}")
    private String uploadBasePath;

    // -----------------------------------------------------------------------
    // Dashboard
    // -----------------------------------------------------------------------

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication auth) {
        List<WardPatientAssignment> activeAssignments = wardNurseService.getActiveAssignments();

        long critical = activeAssignments.stream()
                .filter(a -> a.getStatus() == WardPatientStatus.CRITICAL).count();
        long admitted = activeAssignments.stream()
                .filter(a -> a.getStatus() == WardPatientStatus.ADMITTED).count();
        long stable   = activeAssignments.stream()
                .filter(a -> a.getStatus() == WardPatientStatus.STABLE).count();

        model.addAttribute("activeAssignments", activeAssignments);
        model.addAttribute("totalPatients",     activeAssignments.size());
        model.addAttribute("criticalCount",     critical);
        model.addAttribute("admittedCount",     admitted);
        model.addAttribute("stableCount",       stable);
        model.addAttribute("activePage", "dashboard");
        return "ward-nurse/dashboard";
    }

    // -----------------------------------------------------------------------
    // Patient list
    // -----------------------------------------------------------------------

    @GetMapping("/patients")
    public String patients(Model model) {
        List<WardPatientAssignment> assignments = wardNurseService.getActiveAssignments();
        model.addAttribute("assignments", assignments);
        model.addAttribute("statusValues", WardPatientStatus.values());
        model.addAttribute("activePage", "patients");
        return "ward-nurse/patients";
    }

    // -----------------------------------------------------------------------
    // Patient care (vitals, meds, wound care)
    // -----------------------------------------------------------------------

    @GetMapping("/patients/{assignmentId}/care")
    public String patientCare(@PathVariable UUID assignmentId, Model model) {
        WardPatientAssignment assignment = wardNurseService.getAssignment(assignmentId);
        List<VitalSigns>                       recentVitals = wardNurseService.getRecentVitals(assignmentId);
        List<MedicationAdministrationRecord>   medications  = wardNurseService.getMedicationRecords(assignmentId);
        List<WoundCareNote>                    woundNotes   = wardNurseService.getWoundCareNotes(assignmentId);

        // Nurses for re-assignment dropdown
        List<Staff> nurses = new java.util.ArrayList<>(staffRepository.findByStaffRoleAndActiveTrue(Role.WARD_NURSE));
        nurses.addAll(staffRepository.findByStaffRoleAndActiveTrue(Role.NURSE));

        boolean wardInvoicePaid = wardNurseService.isWardInvoicePaid(assignment);

        model.addAttribute("assignment",         assignment);
        model.addAttribute("patient",            assignment.getPatient());
        model.addAttribute("recentVitals",       recentVitals);
        model.addAttribute("medications",        medications);
        model.addAttribute("woundNotes",         woundNotes);
        model.addAttribute("wardPrescriptions",  wardNurseService.getWardPrescriptionsForNurse(assignmentId));
        model.addAttribute("skipReasons",        MedicationSkipReason.values());
        model.addAttribute("statusValues",       WardPatientStatus.values());
        model.addAttribute("nurses",             nurses);
        model.addAttribute("wardOptions",        WardOption.getAll());
        model.addAttribute("wardInvoicePaid",    wardInvoicePaid);
        model.addAttribute("wardInvoiceId",      assignment.getWardInvoiceId());
        model.addAttribute("activePage", "patients");
        return "ward-nurse/patient-care";
    }

    // -----------------------------------------------------------------------
    // Record vitals — POST
    // -----------------------------------------------------------------------

    @PostMapping("/patients/{assignmentId}/vitals")
    public String recordVitals(@PathVariable UUID assignmentId,
                               @RequestParam(required = false) Integer bpSystolic,
                               @RequestParam(required = false) Integer bpDiastolic,
                               @RequestParam(required = false) Integer pulseRate,
                               @RequestParam(required = false) String  temperature,
                               @RequestParam(required = false) Integer respiratoryRate,
                               @RequestParam(required = false) Integer spo2,
                               @RequestParam(required = false) Integer painScore,
                               @RequestParam(required = false) String  notes,
                               Authentication auth,
                               RedirectAttributes redirectAttrs) {
        try {
            Staff nurse = getNurse(auth);
            VitalSigns vitals = VitalSigns.builder()
                    .bpSystolic(bpSystolic)
                    .bpDiastolic(bpDiastolic)
                    .pulseRate(pulseRate)
                    .temperature(temperature != null && !temperature.isBlank()
                            ? new BigDecimal(temperature) : null)
                    .respiratoryRate(respiratoryRate)
                    .spo2(spo2)
                    .painScore(painScore)
                    .notes(notes)
                    .recordedBy(nurse)
                    .recordedAt(LocalDateTime.now())
                    .build();
            wardNurseService.recordVitals(assignmentId, vitals);
            redirectAttrs.addFlashAttribute("successMsg", "Vitals recorded successfully.");
        } catch (Exception e) {
            log.error("Error recording vitals for assignment {}", assignmentId, e);
            redirectAttrs.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
        }
        return "redirect:/ward-nurse/patients/" + assignmentId + "/care";
    }

    // -----------------------------------------------------------------------
    // Record medication — POST
    // -----------------------------------------------------------------------

    @PostMapping("/patients/{assignmentId}/medications")
    public String recordMedication(@PathVariable UUID assignmentId,
                                   @RequestParam String  medicationName,
                                   @RequestParam String  scheduledTime,
                                   @RequestParam(required = false) String  doseGiven,
                                   @RequestParam(required = false) String  route,
                                   @RequestParam(defaultValue = "false") boolean wasGiven,
                                   @RequestParam(required = false) String skipReason,
                                   @RequestParam(required = false) String skipNotes,
                                   @RequestParam(required = false) String remarks,
                                   @RequestParam(required = false) UUID wardPrescriptionId,
                                   Authentication auth,
                                   RedirectAttributes redirectAttrs) {
        try {
            Staff nurse = getNurse(auth);
            MedicationAdministrationRecord record = MedicationAdministrationRecord.builder()
                    .medicationName(medicationName)
                    .scheduledTime(LocalDateTime.parse(scheduledTime))
                    .administeredAt(wasGiven ? LocalDateTime.now() : null)
                    .doseGiven(doseGiven)
                    .route(route)
                    .wasGiven(wasGiven)
                    .skipReason(skipReason != null && !skipReason.isBlank()
                            ? MedicationSkipReason.valueOf(skipReason) : null)
                    .skipNotes(skipNotes)
                    .remarks(remarks)
                    .administeredBy(nurse)
                    .build();
            wardNurseService.recordMedication(assignmentId, record, wardPrescriptionId);
            redirectAttrs.addFlashAttribute("successMsg",
                    wasGiven ? "Medication administered and recorded." : "Medication skipped and recorded.");
        } catch (Exception e) {
            log.error("Error recording medication for assignment {}", assignmentId, e);
            redirectAttrs.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
        }
        return "redirect:/ward-nurse/patients/" + assignmentId + "/care";
    }

    // -----------------------------------------------------------------------
    // Record wound care — POST
    // -----------------------------------------------------------------------

    @PostMapping("/patients/{assignmentId}/wound-care")
    public String recordWoundCare(@PathVariable UUID assignmentId,
                                  @RequestParam(required = false) String  woundAppearance,
                                  @RequestParam(defaultValue = "false") boolean dressingChanged,
                                  @RequestParam(required = false) String  dressingType,
                                  @RequestParam(defaultValue = "false") boolean signsOfInfection,
                                  @RequestParam(required = false) String  infectionDescription,
                                  @RequestParam(required = false) String  remarks,
                                  Authentication auth,
                                  RedirectAttributes redirectAttrs) {
        try {
            Staff nurse = getNurse(auth);
            WoundCareNote note = WoundCareNote.builder()
                    .woundAppearance(woundAppearance)
                    .dressingChanged(dressingChanged)
                    .dressingType(dressingType)
                    .signsOfInfection(signsOfInfection)
                    .infectionDescription(infectionDescription)
                    .remarks(remarks)
                    .recordedBy(nurse)
                    .recordedAt(LocalDateTime.now())
                    .build();
            wardNurseService.recordWoundCare(assignmentId, note);
            redirectAttrs.addFlashAttribute("successMsg", "Wound care note saved.");
        } catch (Exception e) {
            log.error("Error recording wound care for assignment {}", assignmentId, e);
            redirectAttrs.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
        }
        return "redirect:/ward-nurse/patients/" + assignmentId + "/care";
    }

    // -----------------------------------------------------------------------
    // Update patient status — POST
    // -----------------------------------------------------------------------

    @PostMapping("/patients/{assignmentId}/status")
    public String updateStatus(@PathVariable UUID assignmentId,
                               @RequestParam String status,
                               RedirectAttributes redirectAttrs) {
        try {
            wardNurseService.updateStatus(assignmentId, WardPatientStatus.valueOf(status));
            redirectAttrs.addFlashAttribute("successMsg", "Patient status updated to " + status + ".");
        } catch (Exception e) {
            log.error("Error updating status for assignment {}", assignmentId, e);
            redirectAttrs.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
        }
        return "redirect:/ward-nurse/patients/" + assignmentId + "/care";
    }

    // -----------------------------------------------------------------------
    // Assign / update ward & bed — POST
    // -----------------------------------------------------------------------

    @PostMapping("/patients/{assignmentId}/assign-bed")
    public String assignBed(@PathVariable UUID assignmentId,
                            @RequestParam String ward,
                            @RequestParam String bedNumber,
                            @RequestParam(required = false) String nurseId,
                            RedirectAttributes redirectAttrs) {
        try {
            wardNurseService.updateBedInfo(assignmentId, ward, bedNumber);
            if (nurseId != null && !nurseId.isBlank()) {
                wardNurseService.assignNurse(assignmentId, UUID.fromString(nurseId));
            }
            redirectAttrs.addFlashAttribute("successMsg", "Ward and bed assigned successfully.");
        } catch (Exception e) {
            log.error("Error assigning bed for assignment {}", assignmentId, e);
            redirectAttrs.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
        }
        return "redirect:/ward-nurse/patients/" + assignmentId + "/care";
    }

    // -----------------------------------------------------------------------
    // Surgery queue & detail for nurses
    // -----------------------------------------------------------------------

    @GetMapping("/surgeries")
    public String surgeriesQueue(Model model, Authentication auth) {
        model.addAttribute("surgeries", surgeryService.getActiveSurgeriesForNurse());
        model.addAttribute("activePage", "surgeries");
        return "ward-nurse/surgery-queue";
    }

    @GetMapping("/surgeries/{surgeryOrderId}")
    public String surgeryDetail(@PathVariable java.util.UUID surgeryOrderId, Model model) {
        model.addAttribute("surgery", surgeryService.getSurgeryDetail(surgeryOrderId));
        model.addAttribute("pharmacyItems", surgeryService.getAllPharmacyItems());
        model.addAttribute("activePage", "surgeries");
        return "ward-nurse/surgery-detail";
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

    @GetMapping("/surgeries/{surgeryOrderId}/consent-download")
    public ResponseEntity<Resource> downloadConsentDocument(@PathVariable UUID surgeryOrderId) {
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

    @PostMapping("/surgeries/{surgeryOrderId}/discharge")
    public String dischargePatient(@PathVariable UUID surgeryOrderId,
                                   RedirectAttributes redirectAttrs) {
        try {
            surgeryService.updateStatus(surgeryOrderId,
                    com.adags.hospital.domain.surgery.SurgeryStatus.DISCHARGED);
            redirectAttrs.addFlashAttribute("successMsg", "Patient discharged successfully.");
        } catch (Exception e) {
            log.error("Error discharging patient for surgery {}: {}", surgeryOrderId, e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
        }
        return "redirect:/ward-nurse/surgeries/" + surgeryOrderId;
    }

    @PostMapping("/surgeries/{surgeryOrderId}/intraop")
    public String saveIntraoperative(@PathVariable UUID surgeryOrderId,
                                     @RequestParam(required = false) String leadSurgeon,
                                     @RequestParam(required = false) String anesthesiologist,
                                     @RequestParam(required = false) String startTime,
                                     @RequestParam(required = false) String endTime,
                                     @RequestParam(required = false) Integer bloodLossMl,
                                     @RequestParam(required = false) Integer fluidsGivenMl,
                                     @RequestParam(required = false) String complications,
                                     @RequestParam(required = false) String intraopNotes,
                                     @RequestParam(required = false) String anesthesiaNotes,
                                     RedirectAttributes redirectAttrs) {
        try {
            java.time.LocalDateTime start = (startTime != null && !startTime.isBlank())
                    ? java.time.LocalDateTime.parse(startTime) : null;
            java.time.LocalDateTime end = (endTime != null && !endTime.isBlank())
                    ? java.time.LocalDateTime.parse(endTime) : null;
            surgeryService.saveIntraoperative(surgeryOrderId, leadSurgeon, anesthesiologist,
                    start, end, bloodLossMl, fluidsGivenMl, complications, intraopNotes, anesthesiaNotes);
            redirectAttrs.addFlashAttribute("successMsg", "Intraoperative record saved.");
        } catch (Exception e) {
            log.error("Error saving intraop for surgery {}: {}", surgeryOrderId, e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
        }
        return "redirect:/ward-nurse/surgeries/" + surgeryOrderId;
    }

    @PostMapping("/surgeries/{surgeryOrderId}/item")
    public String addItem(@PathVariable UUID surgeryOrderId,
                          @RequestParam String itemType,
                          @RequestParam String itemName,
                          @RequestParam(defaultValue = "1") int quantity,
                          @RequestParam(required = false) java.math.BigDecimal price,
                          @RequestParam(defaultValue = "ORAL") String route,
                          @RequestParam(required = false) String instructions,
                          @RequestParam(defaultValue = "false") boolean dispenseAsWhole,
                          @RequestParam(required = false) Integer dosesPerDay,
                          @RequestParam(required = false) Integer quantityPerDose,
                          @RequestParam(required = false) Integer numberOfDays,
                          RedirectAttributes redirectAttrs) {
        try {
            com.adags.hospital.domain.surgery.SurgeryItemListType type =
                    com.adags.hospital.domain.surgery.SurgeryItemListType.valueOf(itemType);
            surgeryService.addItem(surgeryOrderId, type, itemName, quantity, price,
                    route, instructions, dispenseAsWhole, dosesPerDay, quantityPerDose, numberOfDays);
            redirectAttrs.addFlashAttribute("successMsg",
                    (type == com.adags.hospital.domain.surgery.SurgeryItemListType.PRE_OP
                            ? "Pre-op" : "Post-op") + " item added.");
        } catch (Exception e) {
            log.error("Error adding item to surgery {}: {}", surgeryOrderId, e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
        }
        String tab = "PRE_OP".equals(itemType) ? "#tabPreop" : "#tabPostopItems";
        return "redirect:/ward-nurse/surgeries/" + surgeryOrderId + tab;
    }

    @PostMapping("/surgeries/{surgeryOrderId}/lab-item")
    public String addLabItem(@PathVariable UUID surgeryOrderId,
                             @RequestParam String itemType,
                             @RequestParam String itemName,
                             @RequestParam(required = false) String testCode,
                             @RequestParam(defaultValue = "1") int quantity,
                             @RequestParam(required = false) java.math.BigDecimal price,
                             RedirectAttributes redirectAttrs) {
        try {
            com.adags.hospital.domain.surgery.SurgeryItemListType type =
                    com.adags.hospital.domain.surgery.SurgeryItemListType.valueOf(itemType);
            surgeryService.addLabItem(surgeryOrderId, type, itemName, testCode, quantity, price);
            redirectAttrs.addFlashAttribute("successMsg", "Lab request added successfully.");
        } catch (Exception e) {
            log.error("Error adding lab item to surgery {}: {}", surgeryOrderId, e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
        }
        return "redirect:/ward-nurse/surgeries/" + surgeryOrderId;
    }

    @PostMapping("/surgeries/{surgeryOrderId}/postop")
    public String recordPostopCare(@PathVariable java.util.UUID surgeryOrderId,
                                   @RequestParam(required = false) String consciousnessLevel,
                                   @RequestParam(required = false) String bloodPressure,
                                   @RequestParam(required = false) Integer pulseRate,
                                   @RequestParam(required = false) String spo2,
                                   @RequestParam(required = false) Integer painScore,
                                   @RequestParam(required = false) String temperature,
                                   @RequestParam(required = false) String recoveryNotes,
                                   @RequestParam(required = false) String nextStep,
                                   Authentication auth,
                                   org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        try {
            Staff nurse = getNurse(auth);
            java.math.BigDecimal spo2Dec = (spo2 != null && !spo2.isBlank())
                    ? new java.math.BigDecimal(spo2) : null;
            java.math.BigDecimal tempDec = (temperature != null && !temperature.isBlank())
                    ? new java.math.BigDecimal(temperature) : null;
            surgeryService.savePostopCare(surgeryOrderId,
                    nurse != null ? nurse.getId() : null,
                    consciousnessLevel, bloodPressure, pulseRate,
                    spo2Dec, painScore, tempDec, recoveryNotes, nextStep);
            redirectAttrs.addFlashAttribute("successMsg", "Post-op care record saved.");
        } catch (Exception e) {
            log.error("Error recording post-op care for surgery {}: {}", surgeryOrderId, e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
        }
        return "redirect:/ward-nurse/surgeries/" + surgeryOrderId;
    }

    // -----------------------------------------------------------------------
    // Create surgery for an already-admitted patient (patient stays admitted)
    // -----------------------------------------------------------------------

    @GetMapping("/patients/{assignmentId}/surgery")
    public String showSurgeryCreateForm(@PathVariable UUID assignmentId, Model model) {
        WardPatientAssignment assignment = wardNurseService.getAssignment(assignmentId);
        model.addAttribute("assignment",       assignment);
        model.addAttribute("patient",          assignment.getPatient());
        model.addAttribute("urgencyValues",    SurgeryUrgency.values());
        model.addAttribute("anesthesiaValues", AnesthesiaType.values());
        model.addAttribute("availableNurses",  surgeryService.getAvailableNurses());
        model.addAttribute("procedures",       surgeryService.searchSurgeryProcedures(""));
        model.addAttribute("activePage",       "patients");
        return "ward-nurse/surgery-create";
    }

    @PostMapping("/patients/{assignmentId}/surgery")
    public String createSurgeryFromWard(@PathVariable UUID assignmentId,
                                        @RequestParam(required = false) UUID servicePriceItemId,
                                        @RequestParam String procedureName,
                                        @RequestParam(required = false) String surgeryType,
                                        @RequestParam(required = false) String urgency,
                                        @RequestParam(required = false) String anesthesiaType,
                                        @RequestParam(required = false) String scheduledDate,
                                        @RequestParam(required = false) String operatingTheater,
                                        @RequestParam(required = false) Integer estimatedDurationMinutes,
                                        @RequestParam(required = false) java.math.BigDecimal price,
                                        @RequestParam(required = false) List<UUID> nurseIds,
                                        @RequestParam(required = false) String preopNotes,
                                        @RequestParam(defaultValue = "false") boolean consentObtained,
                                        RedirectAttributes redirectAttrs) {
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
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")));
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
            return "redirect:/ward-nurse/surgeries/" + created.getId();
        } catch (Exception e) {
            log.error("Error creating surgery from ward assignment {}: {}", assignmentId, e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Could not create surgery: " + e.getMessage());
            return "redirect:/ward-nurse/patients/" + assignmentId + "/care";
        }
    }

    @PostMapping("/surgeries/{surgeryOrderId}/send-for-payment")
    public String sendForPayment(@PathVariable UUID surgeryOrderId,
                                 RedirectAttributes redirectAttrs) {
        try {
            surgeryService.sendForPayment(surgeryOrderId);
            redirectAttrs.addFlashAttribute("successMsg",
                    "Surgery invoice sent for payment collection at reception.");
        } catch (Exception e) {
            log.error("Error sending surgery {} for payment: {}", surgeryOrderId, e.getMessage(), e);
            redirectAttrs.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
        }
        return "redirect:/ward-nurse/surgeries/" + surgeryOrderId;
    }

    // -----------------------------------------------------------------------
    // My History
    // -----------------------------------------------------------------------

    @GetMapping("/history")
    public String history(Model model, Authentication auth) {
        Staff nurse = getNurse(auth);
        if (nurse == null) return "redirect:/ward-nurse/dashboard";
        List<WardPatientAssignment> history = wardNurseService.getMyHistory(nurse.getId());
        long dischargedCount = history.stream()
                .filter(a -> a.getStatus() == WardPatientStatus.DISCHARGED)
                .count();
        long criticalCount = history.stream()
                .filter(a -> a.getStatus() == WardPatientStatus.CRITICAL)
                .count();
        long activeCount = history.stream()
                .filter(a -> a.getStatus() != WardPatientStatus.DISCHARGED
                          && a.getStatus() != WardPatientStatus.TRANSFERRED)
                .count();
        model.addAttribute("history", history);
        model.addAttribute("dischargedCount", dischargedCount);
        model.addAttribute("criticalCount", criticalCount);
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("activePage", "history");
        return "ward-nurse/history";
    }

    // -----------------------------------------------------------------------
    // Billing overview (read-only)
    // -----------------------------------------------------------------------

    @GetMapping("/billing")
    public String billingRedirect() {
        return "redirect:/ward-nurse/billing/ward";
    }

    @GetMapping("/billing/ward")
    public String billingWard(Model model) {
        model.addAttribute("activePage", "billing-ward");
        model.addAttribute("patients", wardNurseService.getBillingOverview());
        return "ward-nurse/billing";
    }

    @GetMapping("/billing/surgery")
    public String billingSurgery(Model model) {
        model.addAttribute("activePage", "billing-surgery");
        model.addAttribute("surgeries", wardNurseService.getSurgeryBillingOverview());
        return "ward-nurse/billing-surgery";
    }

    @GetMapping("/billing/{assignmentId}")
    public String billingDetail(@PathVariable UUID assignmentId, Model model,
            RedirectAttributes redirectAttrs) {
        try {
            model.addAttribute("activePage", "billing-ward");
            model.addAttribute("bill", wardNurseService.getPatientBillDetail(assignmentId));
            return "ward-nurse/billing-detail";
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/ward-nurse/billing/ward";
        }
    }

    // -----------------------------------------------------------------------
    // Helper: resolve currently logged-in nurse's Staff record
    // -----------------------------------------------------------------------

    private Staff getNurse(Authentication auth) {
        if (auth == null) return null;
        AppUser user = userRepository.findByUsernameWithStaff(auth.getName()).orElse(null);
        return user != null ? user.getStaff() : null;
    }
}
