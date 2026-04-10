package com.adags.hospital.controller;

import com.adags.hospital.domain.appointment.Appointment;
import com.adags.hospital.domain.billing.Invoice;
import com.adags.hospital.domain.lab.LabRequest;
import com.adags.hospital.domain.medicalrecord.ConsultationStatus;
import com.adags.hospital.domain.medicalrecord.MedicalRecord;
import com.adags.hospital.domain.medicalrecord.Prescription;
import com.adags.hospital.domain.patient.Patient;
import com.adags.hospital.domain.pricing.ServicePriceItem;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.user.AppUser;
import com.adags.hospital.repository.appointment.AppointmentRepository;
import com.adags.hospital.repository.lab.LabRequestRepository;
import com.adags.hospital.repository.medicalrecord.MedicalRecordRepository;
import com.adags.hospital.repository.medicalrecord.PrescriptionRepository;
import com.adags.hospital.domain.pharmacy.StockItem;
import com.adags.hospital.repository.pharmacy.DispensedItemRepository;
import com.adags.hospital.repository.pharmacy.StockItemRepository;
import com.adags.hospital.service.pharmacy.StockService;
import com.adags.hospital.repository.patient.PatientRepository;
import com.adags.hospital.repository.pricing.ServicePriceItemRepository;
import com.adags.hospital.repository.staff.StaffRepository;
import com.adags.hospital.repository.user.UserRepository;
import com.adags.hospital.service.consultation.ConsultationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST endpoints for the doctor's consultation page and dedicated
 * Prescriptions / Lab Requests pages.
 * Served by the session-secured web filter chain (no JWT needed).
 */
@Slf4j
@RestController
@RequestMapping("/doctor/api")
@RequiredArgsConstructor
public class DoctorApiController {

    private final ConsultationService       consultationService;
    private final ServicePriceItemRepository priceItemRepository;
    private final UserRepository            userRepository;
    private final StaffRepository           staffRepository;
    private final PatientRepository         patientRepository;
    private final MedicalRecordRepository   medicalRecordRepository;
    private final PrescriptionRepository    prescriptionRepository;
    private final LabRequestRepository      labRequestRepository;
    private final AppointmentRepository     appointmentRepository;
    private final DispensedItemRepository   dispensedItemRepository;
    private final StockItemRepository       stockItemRepository;

    /**
     * Returns all patients who have an OPEN visit assigned to the logged-in
     * doctor, de-duplicated by patient — newest visit first per patient.
     * Used to populate the active-patient list on the Rx and Lab pages.
     */
    @GetMapping("/my-patients")
    public ResponseEntity<List<Map<String, Object>>> getMyActivePatients(Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return ResponseEntity.status(403).body(List.of());

        List<MedicalRecord> records =
                medicalRecordRepository.findByDoctorIdAndStatus(doctor.getId(), ConsultationStatus.OPEN);

        // De-duplicate: one entry per patient, keep most-recent open visit date + count
        Map<UUID, Map<String, Object>> seen = new java.util.LinkedHashMap<>();
        for (MedicalRecord r : records) {
            UUID pid = r.getPatient().getId();
            if (!seen.containsKey(pid)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",          pid);
                m.put("firstName",   r.getPatient().getFirstName());
                m.put("lastName",    r.getPatient().getLastName());
                m.put("nationalId",  r.getPatient().getNationalId() != null ? r.getPatient().getNationalId() : "");
                m.put("phone",       r.getPatient().getPhone() != null ? r.getPatient().getPhone() : "");
                m.put("lastVisit",   r.getVisitDate() != null ? r.getVisitDate().toString() : "");
                m.put("openVisits",  1);
                seen.put(pid, m);
            } else {
                seen.get(pid).merge("openVisits", 1, (a, b) -> (int) a + (int) b);
            }
        }
        return ResponseEntity.ok(new java.util.ArrayList<>(seen.values()));
    }

    // -----------------------------------------------------------------------
    // Patient search — used by the autocomplete on Rx and Lab pages
    // -----------------------------------------------------------------------

    /** Search active patients by name, national ID or phone (min 2 chars). */
    @GetMapping("/patients/search")
    public ResponseEntity<List<Map<String, Object>>> searchPatients(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(
                patientRepository.searchPatients(q.trim(), PageRequest.of(0, 10))
                        .getContent().stream()
                        .map(p -> {
                            Map<String, Object> m = new java.util.LinkedHashMap<>();
                            m.put("id",         p.getId());
                            m.put("firstName",  p.getFirstName());
                            m.put("lastName",   p.getLastName());
                            m.put("nationalId", p.getNationalId() != null ? p.getNationalId() : "");
                            m.put("phone",      p.getPhone() != null ? p.getPhone() : "");
                            return m;
                        })
                        .collect(Collectors.toList())
        );
    }

    /**
     * Returns the medical records for a patient that are attended by the
     * logged-in doctor, newest first.  Used by the Rx and Lab pages to
     * populate the visit accordion after patient selection.
     */
    @GetMapping("/patients/{patientId}/records")
    public ResponseEntity<List<Map<String, Object>>> getPatientRecords(
            @PathVariable UUID patientId) {
        // Show ALL visits for the patient so the doctor can act on any visit.
        // (Doctor-only filtering was too restrictive — if the visit was assigned
        //  to a different doctor the list was always empty.)
        List<MedicalRecord> records =
                medicalRecordRepository.findAllByPatientIdOrdered(patientId);
        List<Map<String, Object>> result = records.stream().map(r -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id",           r.getId());
            m.put("visitDate",    r.getVisitDate() != null ? r.getVisitDate().toString() : "");
            m.put("status",       r.getConsultationStatus().name());
            m.put("doctorName",   r.getAttendingDoctor() != null
                                      ? r.getAttendingDoctor().getFirstName() + " " + r.getAttendingDoctor().getLastName()
                                      : "");
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * Create a new (OPEN) visit for the given patient, assigned to the
     * logged-in doctor.  Used by the Rx and Lab pages when no suitable visit
     * exists or the user wants to start a fresh visit.
     */
    @PostMapping("/patients/{patientId}/visits")
    public ResponseEntity<Map<String, Object>> createVisit(
            @PathVariable UUID patientId,
            Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return ResponseEntity.status(403).body(Map.of("success", false, "message", "Unauthorized"));

        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Patient not found"));

        MedicalRecord record = MedicalRecord.builder()
                .patient(patient)
                .attendingDoctor(doctor)
                .visitDate(LocalDateTime.now())
                .consultationStatus(ConsultationStatus.OPEN)
                .build();
        record = medicalRecordRepository.save(record);

        Map<String, Object> visit = new LinkedHashMap<>();
        visit.put("id",         record.getId());
        visit.put("visitDate",  record.getVisitDate().toString());
        visit.put("status",     record.getConsultationStatus().name());
        visit.put("doctorName", doctor.getFirstName() + " " + doctor.getLastName());

        log.info("New visit {} created for patient {} by Dr {}",
                record.getId(), patientId, doctor.getFirstName());
        return ResponseEntity.ok(Map.of("success", true, "visit", visit));
    }

    // -----------------------------------------------------------------------
    // Catalogue search — used by the autocomplete on Rx and Lab pages
    // -----------------------------------------------------------------------

    /** Search pharmacy items that are currently in stock (currentQuantity > 0). */
    @GetMapping("/catalogue/pharmacy/search")
    public ResponseEntity<List<Map<String, Object>>> searchPharmacy(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(
                stockItemRepository.searchInStockPharmacyItems(q.trim()).stream()
                        .limit(20)
                        .map(s -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("id",             s.getPriceItem().getId());
                            m.put("name",           s.getPriceItem().getProductName());
                            m.put("classification", s.getPriceItem().getClassification() != null
                                                        ? s.getPriceItem().getClassification() : "");
                            m.put("price",          s.getPriceItem().getPrice() != null
                                                        ? s.getPriceItem().getPrice() : BigDecimal.ZERO);
                            m.put("stockQty",       s.getCurrentQuantity());
                            return m;
                        })
                        .collect(Collectors.toList())
        );
    }

    /** All in-stock medicines for the Hospital Catalogue page. */
    @GetMapping("/catalogue/medicines/all")
    public ResponseEntity<List<Map<String, Object>>> getAllInStockMedicines() {
        return ResponseEntity.ok(
                stockItemRepository.findAllInStockPharmacyItems().stream()
                        .map(s -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("id",              s.getPriceItem().getId());
                            m.put("name",            s.getPriceItem().getProductName());
                            m.put("classification",  s.getPriceItem().getClassification() != null
                                                         ? s.getPriceItem().getClassification() : "");
                            m.put("price",           s.getPriceItem().getPrice() != null
                                                         ? s.getPriceItem().getPrice() : BigDecimal.ZERO);
                            m.put("currentQuantity", s.getCurrentQuantity());
                            m.put("stockStatus",     StockService.computeStatus(s));
                            return m;
                        })
                        .collect(Collectors.toList())
        );
    }

    /** All lab tests for the Hospital Catalogue page. */
    @GetMapping("/catalogue/lab/all")
    public ResponseEntity<List<Map<String, Object>>> getAllLabTests() {
        return ResponseEntity.ok(
                priceItemRepository.findAllLabTests().stream()
                        .map(i -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("id",             i.getId());
                            m.put("name",           i.getProductName());
                            m.put("classification", i.getClassification() != null ? i.getClassification() : "");
                            m.put("productCode",    i.getProductCode() != null ? i.getProductCode() : "");
                            m.put("price",          i.getPrice() != null ? i.getPrice() : BigDecimal.ZERO);
                            return m;
                        })
                        .collect(Collectors.toList())
        );
    }

    /** Search lab test price catalogue (used on the Lab Requests page). */
    @GetMapping("/catalogue/lab/search")
    public ResponseEntity<List<Map<String, Object>>> searchLabTests(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(
                priceItemRepository.searchLabTests(q.trim()).stream()
                        .limit(20)
                        .map(i -> Map.<String, Object>of(
                                "id",             i.getId(),
                                "name",           i.getProductName(),
                                "classification", i.getClassification() != null ? i.getClassification() : "",
                                "productCode",    i.getProductCode() != null ? i.getProductCode() : "",
                                "price",          i.getPrice() != null ? i.getPrice() : BigDecimal.ZERO
                        ))
                        .collect(Collectors.toList())
        );
    }

    // -----------------------------------------------------------------------
    // Prescriptions API
    // -----------------------------------------------------------------------

    /** Add a prescription to a medical record. */
    @PostMapping("/prescriptions/{recordId}/add")
    public ResponseEntity<Map<String, Object>> addPrescription(
            @PathVariable UUID recordId,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return unauthorized();
        try {
            UUID priceItemId = UUID.fromString(body.get("priceItemId"));
            boolean dispenseAsWhole = Boolean.parseBoolean(body.getOrDefault("dispenseAsWhole", "false"));
            Integer dosesPerDay     = parseIntOrNull(body.get("dosesPerDay"));
            Integer quantityPerDose = dispenseAsWhole ? null : parseIntOrNull(body.get("quantityPerDose"));
            Integer numberOfDays    = dispenseAsWhole ? null : parseIntOrNull(body.get("numberOfDays"));

            Prescription px = consultationService.addPrescription(
                    recordId, priceItemId,
                    body.get("route"), body.get("instructions"),
                    dosesPerDay, quantityPerDose, numberOfDays, dispenseAsWhole);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success",    true);
            resp.put("id",         px.getId());
            resp.put("drug",       px.getPriceItem() != null
                                       ? px.getPriceItem().getProductName()
                                       : (px.getMedication() != null ? px.getMedication().getGenericName() : ""));
            resp.put("totalQty",   px.getTotalQuantityToDispense() != null ? px.getTotalQuantityToDispense() : 1);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Error adding prescription to record {}: {}", recordId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** Remove an undispensed prescription. */
    @DeleteMapping("/prescriptions/{prescriptionId}")
    public ResponseEntity<Map<String, Object>> removePrescription(
            @PathVariable UUID prescriptionId,
            Authentication auth) {
        if (getDoctor(auth) == null) return unauthorized();
        try {
            consultationService.removePrescription(prescriptionId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** Send all undispensed prescriptions for a record to payment (creates ISSUED invoices). */
    @PostMapping("/prescriptions/{recordId}/send-to-payment")
    public ResponseEntity<Map<String, Object>> sendPrescriptionsToPayment(
            @PathVariable UUID recordId,
            Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return unauthorized();
        try {
            List<Invoice> invoices = consultationService.sendPrescriptionsToPayment(recordId, doctor);
            BigDecimal total = invoices.stream()
                    .map(Invoice::getTotalAmount).filter(v -> v != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String numbers = invoices.stream()
                    .map(Invoice::getInvoiceNumber).collect(Collectors.joining(", "));
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "invoiceNumbers", numbers,
                    "total", total,
                    "count", invoices.size(),
                    "message", invoices.size() + " pharmacy invoice(s) sent to reception: " + numbers
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error sending prescriptions to payment for record {}: {}", recordId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "An error occurred. Please try again."));
        }
    }

    // -----------------------------------------------------------------------
    // Lab Requests API
    // -----------------------------------------------------------------------

    /** Add a lab request to a medical record. */
    @PostMapping("/lab/{recordId}/add")
    public ResponseEntity<Map<String, Object>> addLabRequest(
            @PathVariable UUID recordId,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return unauthorized();
        try {
            UUID priceItemId = body.get("priceItemId") != null
                    ? UUID.fromString(body.get("priceItemId")) : null;
            LabRequest lr = consultationService.addLabRequest(
                    recordId, doctor, priceItemId,
                    body.get("testName"), body.get("urgency"), body.get("instructions"));
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "id", lr.getId(),
                    "testName", lr.getTestName(),
                    "urgency", lr.getUrgency().name()
            ));
        } catch (Exception e) {
            log.error("Error adding lab request to record {}: {}", recordId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** Remove a PENDING lab request. */
    @DeleteMapping("/lab/{labRequestId}")
    public ResponseEntity<Map<String, Object>> removeLabRequest(
            @PathVariable UUID labRequestId,
            Authentication auth) {
        if (getDoctor(auth) == null) return unauthorized();
        try {
            consultationService.removeLabRequest(labRequestId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** Send all PENDING lab requests for a record to payment. */
    @PostMapping("/lab/{recordId}/send-to-payment")
    public ResponseEntity<Map<String, Object>> sendLabRequestsToPayment(
            @PathVariable UUID recordId,
            Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return unauthorized();
        try {
            List<Invoice> invoices = consultationService.sendLabRequestsToPayment(recordId, doctor);
            BigDecimal total = invoices.stream()
                    .map(Invoice::getTotalAmount).filter(v -> v != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String numbers = invoices.stream()
                    .map(Invoice::getInvoiceNumber).collect(Collectors.joining(", "));
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "invoiceNumbers", numbers,
                    "total", total,
                    "count", invoices.size(),
                    "message", invoices.size() + " lab invoice(s) sent to reception: " + numbers
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error sending lab requests to payment for record {}: {}", recordId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "An error occurred. Please try again."));
        }
    }

    // -----------------------------------------------------------------------
    // Queue API — used by the new Prescriptions & Lab Requests pages
    // -----------------------------------------------------------------------

    /**
     * Returns all patients the doctor should see on the Prescriptions / Lab pages.
     *
     * Source A — MedicalRecord-based (primary): all OPEN or FINALIZED records where
     *   attendingDoctor = this doctor and patient.active = true.
     *   Covers both triage-routed and directly-booked patients, with or without drafts.
     *
     * Source B — Appointment-based (supplement): patients assigned via triage who
     *   do not yet have any medical record (no draft saved yet).
     *
     * De-duplicated by patient; sorted first-come first-served.
     */
    @Transactional(readOnly = true)
    @GetMapping("/queue")
    public ResponseEntity<List<Map<String, Object>>> getPatientQueue(Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return ResponseEntity.status(403).body(List.of());

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        java.util.Set<UUID> seenPatients = new java.util.LinkedHashSet<>();
        int[] pos = {1};

        // Source A: records (OPEN or FINALIZED) where this doctor is the attending
        List<MedicalRecord> records = medicalRecordRepository.findActiveByDoctorAndStatuses(
                doctor.getId(),
                java.util.List.of(ConsultationStatus.OPEN, ConsultationStatus.FINALIZED));
        for (MedicalRecord rec : records) {
            UUID pid = rec.getPatient().getId();
            if (seenPatients.add(pid)) {
                Appointment appt = rec.getAppointment();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("position",      pos[0]++);
                m.put("appointmentId", appt != null ? appt.getId() : null);
                m.put("recordId",      rec.getId());
                m.put("patientId",     pid);
                m.put("firstName",     rec.getPatient().getFirstName());
                m.put("lastName",      rec.getPatient().getLastName());
                m.put("phone",         rec.getPatient().getPhone() != null ? rec.getPatient().getPhone() : "");
                m.put("visitTime",     appt != null && appt.getScheduledDateTime() != null
                                           ? appt.getScheduledDateTime().toString()
                                           : (rec.getVisitDate() != null ? rec.getVisitDate().toString() : ""));
                List<Prescription> rxList = prescriptionRepository.findByMedicalRecordId(rec.getId());
                long pendingRx = rxList.stream()
                        .filter(px -> px.getPharmacyStatus() == com.adags.hospital.domain.medicalrecord.PrescriptionPharmacyStatus.PENDING)
                        .count();
                m.put("rxCount",        rxList.size());
                m.put("pendingRxCount", pendingRx);
                List<com.adags.hospital.domain.lab.LabRequest> labList =
                        labRequestRepository.findByMedicalRecordId(rec.getId());
                long pendingLab = labList.stream()
                        .filter(lr -> lr.getStatus() == com.adags.hospital.domain.lab.LabRequestStatus.PENDING)
                        .count();
                m.put("labCount",       labList.size());
                m.put("pendingLabCount", pendingLab);
                m.put("status",        rec.getConsultationStatus().name());
                result.add(m);
            }
        }

        // Source B: triage-assigned patients with no record yet (supplement)
        for (Appointment a : consultationService.getDoctorQueue(doctor.getId())) {
            UUID pid = a.getPatient().getId();
            if (seenPatients.add(pid)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("position",      pos[0]++);
                m.put("appointmentId", a.getId());
                m.put("recordId",      null);
                m.put("patientId",     pid);
                m.put("firstName",     a.getPatient().getFirstName());
                m.put("lastName",      a.getPatient().getLastName());
                m.put("phone",         a.getPatient().getPhone() != null ? a.getPatient().getPhone() : "");
                m.put("visitTime",     a.getScheduledDateTime() != null ? a.getScheduledDateTime().toString() : "");
                m.put("rxCount",       0);
                m.put("labCount",      0);
                m.put("status",        "PENDING");
                result.add(m);
            }
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Creates a MedicalRecord linked to this appointment if one does not already exist.
     * Called automatically by the Prescriptions page when the doctor tries to prescribe
     * before saving a consultation draft.
     */
    @PostMapping("/appointments/{appointmentId}/ensure-record")
    public ResponseEntity<Map<String, Object>> ensureRecord(
            @PathVariable UUID appointmentId,
            Authentication auth) {
        Staff doctor = getDoctor(auth);
        if (doctor == null) return unauthorized();
        try {
            MedicalRecord rec = medicalRecordRepository.findByAppointmentId(appointmentId).orElse(null);
            if (rec == null) {
                Appointment appt = appointmentRepository.findById(appointmentId).orElse(null);
                if (appt == null) return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Appointment not found"));
                rec = MedicalRecord.builder()
                        .patient(appt.getPatient())
                        .appointment(appt)
                        .attendingDoctor(doctor)
                        .visitDate(java.time.LocalDateTime.now())
                        .consultationStatus(ConsultationStatus.OPEN)
                        .build();
                rec = medicalRecordRepository.save(rec);
            }
            return ResponseEntity.ok(Map.of("success", true, "recordId", rec.getId()));
        } catch (Exception e) {
            log.error("Error ensuring record for appointment {}: {}", appointmentId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "Could not create consultation record"));
        }
    }

    /** List all prescriptions for a medical record (for the Rx management panel). */
    @GetMapping("/prescriptions/{recordId}")
    public ResponseEntity<List<Map<String, Object>>> getPrescriptions(
            @PathVariable UUID recordId,
            Authentication auth) {
        if (getDoctor(auth) == null) return ResponseEntity.status(403).body(List.of());
        return ResponseEntity.ok(
                prescriptionRepository.findByMedicalRecordId(recordId).stream()
                        .map(px -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("id",        px.getId());
                            m.put("drug",      px.getPriceItem() != null
                                                   ? px.getPriceItem().getProductName()
                                                   : (px.getMedication() != null
                                                       ? px.getMedication().getGenericName() : "—"));
                            m.put("dosage",    px.getDosage()    != null ? px.getDosage()    : "");
                            m.put("frequency", px.getFrequency() != null ? px.getFrequency() : "");
                            m.put("duration",  px.getDuration()  != null ? px.getDuration()  : "");
                            m.put("route",     px.getRoute()     != null ? px.getRoute()     : "");
                            m.put("dispensed",       px.isDispensed());
                            m.put("status",          px.getPharmacyStatus() != null
                                                         ? px.getPharmacyStatus().name() : "PENDING");
                            m.put("dispenseAsWhole", px.isDispenseAsWhole());
                            m.put("dosesPerDay",     px.getDosesPerDay());
                            m.put("quantityPerDose", px.getQuantityPerDose());
                            m.put("numberOfDays",    px.getNumberOfDays());
                            m.put("totalQty",        px.getTotalQuantityToDispense() != null
                                                         ? px.getTotalQuantityToDispense() : 1);
                            return m;
                        }).collect(Collectors.toList())
        );
    }

    /**
     * Full prescription history for a medical record, including dispensing details.
     * Used by the "Prescribe History" panel on the doctor's Prescriptions page.
     */
    @Transactional(readOnly = true)
    @GetMapping("/prescriptions/{recordId}/history")
    public ResponseEntity<List<Map<String, Object>>> getPrescriptionHistory(
            @PathVariable UUID recordId,
            Authentication auth) {
        if (getDoctor(auth) == null) return ResponseEntity.status(403).body(List.of());
        return ResponseEntity.ok(
                prescriptionRepository.findByMedicalRecordId(recordId).stream()
                        .map(px -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("id",        px.getId());
                            m.put("drug",      px.getPriceItem() != null
                                                   ? px.getPriceItem().getProductName()
                                                   : (px.getMedication() != null
                                                       ? px.getMedication().getGenericName() : "—"));
                            m.put("dosage",          px.getDosage()    != null ? px.getDosage()    : "");
                            m.put("frequency",       px.getFrequency() != null ? px.getFrequency() : "");
                            m.put("duration",        px.getDuration()  != null ? px.getDuration()  : "");
                            m.put("route",           px.getRoute()     != null ? px.getRoute()     : "");
                            m.put("instructions",    px.getInstructions() != null ? px.getInstructions() : "");
                            m.put("dosesPerDay",     px.getDosesPerDay());
                            m.put("quantityPerDose", px.getQuantityPerDose());
                            m.put("numberOfDays",    px.getNumberOfDays());
                            m.put("totalQty",        px.getTotalQuantityToDispense() != null
                                                         ? px.getTotalQuantityToDispense() : 1);
                            m.put("dispenseAsWhole", px.isDispenseAsWhole());
                            m.put("dispensed",       px.isDispensed());
                            m.put("pharmacyStatus",  px.getPharmacyStatus() != null
                                                         ? px.getPharmacyStatus().name() : "PENDING");

                            // Dispensing detail (who dispensed, when, notes, quantity)
                            var dispensedItems = dispensedItemRepository.findByPrescriptionId(px.getId());
                            if (!dispensedItems.isEmpty()) {
                                var di = dispensedItems.get(0);
                                Map<String, Object> disp = new LinkedHashMap<>();
                                disp.put("quantityDispensed", di.getQuantityDispensed());
                                disp.put("dispensedAt",       di.getDispensedAt() != null ? di.getDispensedAt().toString() : "");
                                disp.put("dispensingNotes",   di.getDispensingNotes() != null ? di.getDispensingNotes() : "");
                                if (di.getDispensedBy() != null) {
                                    disp.put("pharmacistName", di.getDispensedBy().getFirstName()
                                                               + " " + di.getDispensedBy().getLastName());
                                    disp.put("pharmacistRole", di.getDispensedBy().getStaffRole() != null
                                                               ? di.getDispensedBy().getStaffRole().name() : "");
                                } else {
                                    disp.put("pharmacistName", "—");
                                    disp.put("pharmacistRole", "");
                                }
                                m.put("dispenseInfo", disp);
                            } else {
                                m.put("dispenseInfo", null);
                            }
                            return m;
                        }).collect(Collectors.toList())
        );
    }

    /** List all lab requests for a medical record (for the Lab management panel). */
    @GetMapping("/lab/{recordId}")
    public ResponseEntity<List<Map<String, Object>>> getLabRequestsForRecord(
            @PathVariable UUID recordId,
            Authentication auth) {
        if (getDoctor(auth) == null) return ResponseEntity.status(403).body(List.of());
        return ResponseEntity.ok(
                labRequestRepository.findByMedicalRecordId(recordId).stream()
                        .map(lr -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("id",           lr.getId());
                            m.put("testName",     lr.getTestName());
                            m.put("urgency",      lr.getUrgency() != null ? lr.getUrgency().name() : "ROUTINE");
                            m.put("status",       lr.getStatus()  != null ? lr.getStatus().name()  : "PENDING");
                            m.put("instructions", lr.getSpecialInstructions() != null
                                                      ? lr.getSpecialInstructions() : "");
                            return m;
                        }).collect(Collectors.toList())
        );
    }

    /** Full lab request history for a medical record including result / lab-tech details. */
    @Transactional(readOnly = true)
    @GetMapping("/lab/{recordId}/history")
    public ResponseEntity<List<Map<String, Object>>> getLabHistory(
            @PathVariable UUID recordId,
            Authentication auth) {
        if (getDoctor(auth) == null) return ResponseEntity.status(403).body(List.of());
        return ResponseEntity.ok(
                labRequestRepository.findByMedicalRecordId(recordId).stream()
                        .map(lr -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("id",           lr.getId());
                            m.put("testName",     lr.getTestName());
                            m.put("urgency",      lr.getUrgency() != null ? lr.getUrgency().name() : "ROUTINE");
                            m.put("status",       lr.getStatus()  != null ? lr.getStatus().name()  : "PENDING");
                            m.put("instructions", lr.getSpecialInstructions() != null ? lr.getSpecialInstructions() : "");
                            m.put("requestedAt",  lr.getRequestedAt() != null ? lr.getRequestedAt().toString() : "");
                            // Result details (if completed)
                            var result = lr.getResult();
                            if (result != null) {
                                Map<String, Object> res = new LinkedHashMap<>();
                                res.put("resultValue",     result.getResultValue() != null ? result.getResultValue() : "");
                                res.put("referenceRange",  result.getReferenceRange() != null ? result.getReferenceRange() : "");
                                res.put("unit",            result.getUnit() != null ? result.getUnit() : "");
                                res.put("interpretation",  result.getInterpretation() != null ? result.getInterpretation().name() : "");
                                res.put("resultDateTime",  result.getResultDateTime() != null ? result.getResultDateTime().toString() : "");
                                res.put("notes",           result.getNotes() != null ? result.getNotes() : "");
                                if (result.getPerformedBy() != null) {
                                    res.put("performedBy", result.getPerformedBy().getFirstName()
                                                           + " " + result.getPerformedBy().getLastName());
                                } else {
                                    res.put("performedBy", "—");
                                }
                                if (result.getVerifiedBy() != null) {
                                    res.put("verifiedBy", result.getVerifiedBy().getFirstName()
                                                          + " " + result.getVerifiedBy().getLastName());
                                } else {
                                    res.put("verifiedBy", "—");
                                }
                                m.put("result", res);
                            } else {
                                m.put("result", null);
                            }
                            return m;
                        }).collect(Collectors.toList())
        );
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private Staff getDoctor(Authentication auth) {
        if (auth == null) return null;
        AppUser user = userRepository.findByUsernameWithStaff(auth.getName()).orElse(null);
        return user != null ? user.getStaff() : null;
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(403).body(Map.of("success", false, "message", "Unauthorized"));
    }

    private static Integer parseIntOrNull(String s) {
        try { return s != null && !s.isBlank() ? Integer.parseInt(s.trim()) : null; }
        catch (NumberFormatException e) { return null; }
    }
}
