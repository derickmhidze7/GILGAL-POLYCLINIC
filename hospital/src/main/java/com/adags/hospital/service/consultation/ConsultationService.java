package com.adags.hospital.service.consultation;

import com.adags.hospital.domain.appointment.Appointment;
import com.adags.hospital.domain.appointment.AppointmentStatus;
import com.adags.hospital.domain.appointment.AppointmentType;
import com.adags.hospital.domain.billing.Invoice;
import com.adags.hospital.domain.billing.InvoiceLineItem;
import com.adags.hospital.domain.billing.InvoiceStatus;
import com.adags.hospital.domain.billing.LineItemCategory;
import com.adags.hospital.domain.lab.LabRequest;
import com.adags.hospital.domain.lab.LabRequestStatus;
import com.adags.hospital.domain.lab.LabUrgency;
import com.adags.hospital.domain.medicalrecord.*;
import com.adags.hospital.domain.patient.Patient;
import com.adags.hospital.domain.pricing.ServicePriceItem;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.triage.ModeOfAmbulation;
import com.adags.hospital.domain.triage.TriageAssessment;
import com.adags.hospital.dto.consultation.*;
import com.adags.hospital.exception.ResourceNotFoundException;
import com.adags.hospital.repository.appointment.AppointmentRepository;
import com.adags.hospital.repository.billing.InvoiceRepository;
import com.adags.hospital.repository.lab.LabRequestRepository;
import com.adags.hospital.repository.medicalrecord.MedicalRecordRepository;
import com.adags.hospital.repository.medicalrecord.PrescriptionRepository;
import com.adags.hospital.repository.pricing.ServicePriceItemRepository;
import com.adags.hospital.repository.staff.StaffRepository;
import com.adags.hospital.repository.triage.TriageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationService {

    private static final BigDecimal GENERAL_CONSULT_FEE    = new BigDecimal("10000");
    private static final BigDecimal SPECIALIST_CONSULT_FEE = new BigDecimal("30000");

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private final MedicalRecordRepository   medicalRecordRepository;
    private final PrescriptionRepository    prescriptionRepository;
    private final TriageRepository          triageRepository;
    private final AppointmentRepository     appointmentRepository;
    private final StaffRepository           staffRepository;
    private final ServicePriceItemRepository priceItemRepository;
    private final InvoiceRepository         invoiceRepository;
    private final LabRequestRepository      labRequestRepository;
    private final com.adags.hospital.repository.patient.PatientRepository patientRepository;

    // -----------------------------------------------------------------------
    // Doctor queue
    // -----------------------------------------------------------------------

    public List<Appointment> getDoctorQueue(UUID doctorId) {
        return appointmentRepository.findDoctorConsultationQueue(doctorId);
    }

    // -----------------------------------------------------------------------
    // Doctor patient history (most recent 50 finalized/open records)
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MedicalRecord> getDoctorPatientHistory(UUID doctorId) {
        org.springframework.data.domain.Sort sort =
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "visitDate");
        return medicalRecordRepository.findByAttendingDoctorId(
                doctorId,
                PageRequest.of(0, 50, sort)
        ).getContent().stream()
                .peek(r -> {
                    Hibernate.initialize(r.getPatient());
                    Hibernate.initialize(r.getAppointment());
                })
                .toList();
    }

    /**
     * Returns true if the patient's consultation fee invoice is PAID or PARTIALLY_PAID,
     * or if no invoice was generated (free service). Blocks access if ISSUED or DRAFT.
     */
    @Transactional(readOnly = true)
    public boolean isConsultationFeePaid(UUID appointmentId) {
        return triageRepository.findByAppointmentId(appointmentId)
                .map(t -> {
                    Invoice inv = t.getConsultationInvoice();
                    if (inv == null) return true; // no fee required
                    return inv.getStatus() == InvoiceStatus.PAID
                            || inv.getStatus() == InvoiceStatus.PARTIALLY_PAID;
                })
                .orElse(true); // no triage record yet — don't block
    }

    /** Returns the set of appointment IDs (from the given list) whose consultation fee is paid. */
    @Transactional(readOnly = true)
    public Set<UUID> getPaidConsultationAppointmentIds(List<Appointment> appointments) {
        Set<UUID> paid = new HashSet<>();
        for (Appointment a : appointments) {
            if (isConsultationFeePaid(a.getId())) {
                paid.add(a.getId());
            }
        }
        return paid;
    }

    // -----------------------------------------------------------------------
    // Load triage data for consultation page
    // -----------------------------------------------------------------------

    public Optional<TriageAssessment> getTriageForAppointment(UUID appointmentId) {
        return triageRepository.findByAppointmentId(appointmentId);
    }

    @Transactional(readOnly = true)
    public Optional<MedicalRecord> getExistingConsultation(UUID appointmentId) {
        Optional<MedicalRecord> opt = medicalRecordRepository.findByAppointmentId(appointmentId);
        // Force-initialize all lazy collections/associations while the Hibernate session
        // is still open (open-in-view=false means they'd throw LazyInitializationException
        // if accessed later during Thymeleaf template rendering).
        opt.ifPresent(record -> {
            // Initialize forwarded-to-doctor proxy
            Hibernate.initialize(record.getForwardedToDoctor());

            // Initialize prescriptions collection and each prescription's lazy ManyToOne proxies
            Hibernate.initialize(record.getPrescriptions());
            record.getPrescriptions().forEach(px -> {
                Hibernate.initialize(px.getMedication());
                Hibernate.initialize(px.getPriceItem());
            });

            // Initialize lab requests collection and each lab request's lazy result proxy
            Hibernate.initialize(record.getLabRequests());
            record.getLabRequests().forEach(lr -> Hibernate.initialize(lr.getResult()));
        });
        return opt;
    }

    // -----------------------------------------------------------------------
    // Save / Finalize consultation
    // -----------------------------------------------------------------------

    @Transactional
    public MedicalRecord saveConsultation(UUID appointmentId,
                                          Staff doctor,
                                          ConsultationFormRequest form) {
        Appointment appointment = appointmentRepository.findByIdWithPatient(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));

        Patient patient = appointment.getPatient();

        // ── Get or create the medical record ────────────────────────────────
        MedicalRecord record = medicalRecordRepository.findByAppointmentId(appointmentId)
                .orElseGet(() -> MedicalRecord.builder()
                        .patient(patient)
                        .appointment(appointment)
                        .attendingDoctor(doctor)
                        .build());

        // Guard: don't allow editing a LOCKED record
        if (record.getConsultationStatus() == ConsultationStatus.LOCKED) {
            throw new IllegalStateException("This consultation is locked and cannot be modified.");
        }

        // ── History & Examination ────────────────────────────────────────────
        record.setChiefComplaints(form.getChiefComplaints());
        record.setHistoryOfPresentingIllness(form.getHistoryOfPresentingIllness());
        record.setPastMedicalHistory(form.getPastMedicalHistory());
        record.setPastSurgicalHistory(form.getPastSurgicalHistory());
        record.setFamilySocialHistory(form.getFamilySocialHistory());
        record.setDrugFoodAllergies(form.getDrugFoodAllergies());
        record.setComorbidities(form.getComorbidities());
        record.setCurrentSymptoms(form.getCurrentSymptoms());
        record.setModeOfAmbulation(form.getModeOfAmbulation() != null && !form.getModeOfAmbulation().isBlank()
                ? ModeOfAmbulation.valueOf(form.getModeOfAmbulation()) : null);
        record.setHasPain(form.getHasPain());
        record.setPainScore(Boolean.TRUE.equals(form.getHasPain()) ? form.getPainScore() : null);
        record.setPainLocation(Boolean.TRUE.equals(form.getHasPain()) ? form.getPainLocation() : null);
        record.setFallRisk(form.getFallRisk());
        record.setFallScore(Boolean.TRUE.equals(form.getFallRisk()) ? form.getFallScore() : null);
        record.setInfectiousDiseaseRisk(form.getInfectiousDiseaseRisk());
        record.setPhysicalExamination(form.getPhysicalExamination());
        record.setClinicalNotes(form.getClinicalNotes());

        // ── Diagnosis & Treatment ────────────────────────────────────────────
        record.setProvisionalDiagnosis(form.getProvisionalDiagnosis());
        record.setFinalDiagnosis(form.getFinalDiagnosis());
        record.setTreatmentPlan(form.getTreatmentPlan());

        // ── Disposition ──────────────────────────────────────────────────────
        record.setFollowUpDate(form.getFollowUpDate());
        record.setFollowUpInstructions(form.getFollowUpInstructions());
        if (form.getNextStep() != null && !form.getNextStep().isBlank()) {
            try {
                record.setNextStep(DispositionType.valueOf(form.getNextStep()));
            } catch (IllegalArgumentException ignored) {}
        }

        // ── Forward patient to another doctor / specialist ───────────────────
        if (form.getForwardedToDoctorId() != null) {
            Staff targetDoctor = staffRepository.findById(form.getForwardedToDoctorId()).orElse(null);
            if (targetDoctor != null) {
                record.setForwardedToDoctor(targetDoctor);
                record.setForwardedType(
                    targetDoctor.getStaffRole() != null ? targetDoctor.getStaffRole().name() : "DOCTOR");

                // Create a new TRIAGE-type appointment to the next doctor
                Appointment forwardedAppt = Appointment.builder()
                        .patient(patient)
                        .doctor(targetDoctor)
                        .scheduledDateTime(LocalDateTime.now().plusHours(1))
                        .appointmentType(AppointmentType.GENERAL)
                        .status(AppointmentStatus.SCHEDULED)
                        .notes("Forwarded by Dr. " + doctor.getFirstName() + " " + doctor.getLastName()
                               + " from consultation on " + record.getVisitDate().toLocalDate())
                        .build();
                appointmentRepository.save(forwardedAppt);

                // Create a pending consultation invoice so receptionist can collect fee
                boolean isSpecialist = "SPECIALIST_DOCTOR".equals(record.getForwardedType());
                BigDecimal fee = isSpecialist ? SPECIALIST_CONSULT_FEE : GENERAL_CONSULT_FEE;

                Invoice consultInvoice = Invoice.builder()
                        .patient(patient)
                        .invoiceNumber("CONS-" + System.currentTimeMillis())
                        .status(InvoiceStatus.ISSUED)
                        .subtotal(fee)
                        .totalAmount(fee)
                        .notes("Consultation fee for referral to Dr. "
                               + targetDoctor.getFirstName() + " " + targetDoctor.getLastName())
                        .build();

                InvoiceLineItem li = InvoiceLineItem.builder()
                        .invoice(consultInvoice)
                        .description((isSpecialist ? "Specialist" : "General") + " consultation fee")
                        .quantity(1)
                        .unitPrice(fee)
                        .lineTotal(fee)
                        .category(LineItemCategory.CONSULTATION)
                        .build();
                consultInvoice.getLineItems().add(li);
                invoiceRepository.save(consultInvoice);

                log.info("Forwarded appointment created for patient {} to doctor {}; invoice {}",
                         patient.getId(), targetDoctor.getId(), consultInvoice.getInvoiceNumber());
            }
        }

        // ── Status ───────────────────────────────────────────────────────────
        if (form.isFinalize()) {
            // Validate required fields before allowing finalization
            List<String> missingFields = new ArrayList<>();
            if (isBlank(form.getChiefComplaints()))       missingFields.add("Chief Complaints");
            if (isBlank(form.getPhysicalExamination()))   missingFields.add("Physical Examination");
            if (isBlank(form.getProvisionalDiagnosis()))  missingFields.add("Provisional Diagnosis");
            if (isBlank(form.getTreatmentPlan()))         missingFields.add("Treatment Plan");
            if (isBlank(form.getNextStep()))              missingFields.add("Disposition (Next Step)");
            if (!missingFields.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot finalize: the following required fields are missing — "
                        + String.join(", ", missingFields) + ".");
            }

            record.setConsultationStatus(ConsultationStatus.FINALIZED);
            appointment.setStatus(AppointmentStatus.COMPLETED);
            appointmentRepository.save(appointment);

            if (com.adags.hospital.domain.medicalrecord.DispositionType.DISCHARGED.equals(record.getNextStep())) {
                patient.setActive(false);
                patientRepository.save(patient);
            }
        }

        return medicalRecordRepository.save(record);
    }

    // -----------------------------------------------------------------------
    // Dedicated Prescriptions page — service methods
    // -----------------------------------------------------------------------

    /** All open/finalized medical records for a doctor (for the prescriptions page picker). */
    @Transactional(readOnly = true)
    public List<MedicalRecord> getActiveRecordsForDoctor(UUID doctorId) {
        return medicalRecordRepository.findByAttendingDoctorId(
                doctorId,
                org.springframework.data.domain.PageRequest.of(0, 100,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "visitDate")))
                .getContent().stream()
                .filter(r -> r.getConsultationStatus() != ConsultationStatus.LOCKED)
                .peek(r -> {
                    Hibernate.initialize(r.getPatient());
                    Hibernate.initialize(r.getAppointment());
                    r.getPrescriptions().forEach(px -> {
                        Hibernate.initialize(px.getPriceItem());
                        Hibernate.initialize(px.getMedication());
                    });
                    r.getLabRequests().forEach(lr -> {
                        Hibernate.initialize(lr.getServicePriceItem());
                        Hibernate.initialize(lr.getResult());
                    });
                })
                .toList();
    }

    /** Add a single prescription to a medical record. */
    @Transactional
    public com.adags.hospital.domain.medicalrecord.Prescription addPrescription(
            UUID recordId, UUID priceItemId,
            String route, String instructions,
            Integer dosesPerDay, Integer quantityPerDose,
            Integer numberOfDays, boolean dispenseAsWhole) {
        MedicalRecord record = medicalRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("MedicalRecord", "id", recordId));
        ServicePriceItem item = priceItemRepository.findById(priceItemId)
                .orElseThrow(() -> new ResourceNotFoundException("ServicePriceItem", "id", priceItemId));

        // Calculate total units to dispense
        int totalQty;
        if (dispenseAsWhole) {
            totalQty = dosesPerDay != null ? dosesPerDay : 1;
        } else {
            int d = dosesPerDay   != null ? dosesPerDay   : 1;
            int q = quantityPerDose != null ? quantityPerDose : 1;
            int n = numberOfDays  != null ? numberOfDays  : 1;
            totalQty = d * q * n;
        }

        // Build human-readable legacy string fields for display compatibility
        String dosageStr = dispenseAsWhole
                ? "1 unit (whole)"
                : (quantityPerDose != null ? quantityPerDose + " pill(s) per dose" : "—");
        String freqStr = dispenseAsWhole
                ? "As prescribed"
                : (dosesPerDay != null ? dosesPerDay + "x/day" : "—");
        String durStr = dispenseAsWhole
                ? "—"
                : (numberOfDays != null ? numberOfDays + " day(s)" : "—");

        com.adags.hospital.domain.medicalrecord.Prescription px =
                com.adags.hospital.domain.medicalrecord.Prescription.builder()
                        .medicalRecord(record)
                        .priceItem(item)
                        .dosage(dosageStr)
                        .frequency(freqStr)
                        .duration(durStr)
                        .route(route)
                        .instructions(instructions)
                        .dosesPerDay(dosesPerDay)
                        .quantityPerDose(quantityPerDose)
                        .numberOfDays(numberOfDays)
                        .dispenseAsWhole(dispenseAsWhole)
                        .totalQuantityToDispense(totalQty)
                        .dispensed(false)
                        .build();
        record.getPrescriptions().add(px);
        medicalRecordRepository.save(record);
        return px;
    }

    /** Remove an undispensed prescription. */
    @Transactional
    public void removePrescription(UUID prescriptionId) {
        prescriptionRepository.findById(prescriptionId).ifPresent(px -> {
            if (!px.isDispensed()) {
                MedicalRecord record = px.getMedicalRecord();
                record.getPrescriptions().remove(px);
                medicalRecordRepository.save(record);
            }
        });
    }

    /** Create ISSUED invoices for all undispensed prescriptions on a medical record. */
    @Transactional
    public List<Invoice> sendPrescriptionsToPayment(UUID recordId, Staff doctor) {
        MedicalRecord record = medicalRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("MedicalRecord", "id", recordId));
        return createInvoicesForCategory(record, LineItemCategory.PHARMACY, "RX");
    }

    // -----------------------------------------------------------------------
    // Dedicated Lab Requests page — service methods
    // -----------------------------------------------------------------------

    /** Add a single lab request to a medical record. */
    @Transactional
    public LabRequest addLabRequest(UUID recordId, Staff doctor,
                                    UUID priceItemId, String testName,
                                    String urgency, String instructions) {
        MedicalRecord record = medicalRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("MedicalRecord", "id", recordId));
        ServicePriceItem item = (priceItemId != null)
                ? priceItemRepository.findById(priceItemId).orElse(null) : null;
        LabUrgency lu;
        try { lu = LabUrgency.valueOf(urgency != null ? urgency.toUpperCase() : "ROUTINE"); }
        catch (IllegalArgumentException e) { lu = LabUrgency.ROUTINE; }
        LabRequest lr = LabRequest.builder()
                .medicalRecord(record)
                .patient(record.getPatient())
                .requestingDoctor(doctor)
                .testName(testName)
                .testCode(item != null ? item.getProductCode() : null)
                .urgency(lu)
                .specialInstructions(instructions)
                .servicePriceItem(item)
                .build();
        return labRequestRepository.save(lr);
    }

    /** Remove a PENDING lab request. */
    @Transactional
    public void removeLabRequest(UUID labRequestId) {
        labRequestRepository.findById(labRequestId).ifPresent(lr -> {
            if (lr.getStatus() == LabRequestStatus.PENDING) {
                labRequestRepository.delete(lr);
            }
        });
    }

    /** Create ISSUED invoices for all PENDING lab requests on a medical record. */
    @Transactional
    public List<Invoice> sendLabRequestsToPayment(UUID recordId, Staff doctor) {
        MedicalRecord record = medicalRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("MedicalRecord", "id", recordId));
        return createInvoicesForCategory(record, LineItemCategory.LAB, "LAB");
    }

    // -----------------------------------------------------------------------
    // Shared invoice creation helper
    // -----------------------------------------------------------------------

    private List<Invoice> createInvoicesForCategory(MedicalRecord record,
                                                     LineItemCategory cat, String prefix) {
        String visitDate = record.getVisitDate().toLocalDate().toString();

        // Void any existing ISSUED invoices for this category (keep PAID ones)
        invoiceRepository.findByMedicalRecordId(record.getId()).forEach(inv -> {
            if (inv.getStatus() == InvoiceStatus.VOIDED
                    || inv.getStatus() == InvoiceStatus.PAID
                    || inv.getStatus() == InvoiceStatus.PARTIALLY_PAID) return;
            boolean matches = inv.getLineItems().stream()
                    .anyMatch(li -> li.getCategory() == cat);
            if (matches) {
                inv.setStatus(InvoiceStatus.VOIDED);
                invoiceRepository.save(inv);
            }
        });

        List<Invoice> created = new ArrayList<>();

        if (cat == LineItemCategory.LAB) {
            List<LabRequest> pending = labRequestRepository.findByMedicalRecordId(record.getId())
                    .stream()
                    .filter(lr -> lr.getStatus() == LabRequestStatus.PENDING)
                    .toList();
            if (pending.isEmpty())
                throw new IllegalStateException("No pending lab requests to send to payment.");
            for (int i = 0; i < pending.size(); i++) {
                LabRequest lr = pending.get(i);
                BigDecimal price = (lr.getServicePriceItem() != null
                        && lr.getServicePriceItem().getPrice() != null)
                        ? lr.getServicePriceItem().getPrice() : BigDecimal.ZERO;
                String desc = lr.getTestName()
                        + (lr.getTestCode() != null ? " [" + lr.getTestCode() + "]" : "");
                Invoice inv = Invoice.builder()
                        .patient(record.getPatient()).medicalRecord(record)
                        .invoiceNumber(prefix + "-" + System.currentTimeMillis() + "-" + i)
                        .invoiceDate(LocalDateTime.now()).status(InvoiceStatus.ISSUED)
                        .notes("Lab test: " + desc + " — " + visitDate)
                        .subtotal(price).totalAmount(price).build();
                inv.getLineItems().add(InvoiceLineItem.builder()
                        .invoice(inv).description(desc).category(cat)
                        .quantity(1).unitPrice(price).lineTotal(price).build());
                created.add(invoiceRepository.save(inv));
            }
        } else {
            // PHARMACY
            List<com.adags.hospital.domain.medicalrecord.Prescription> rxList =
                    prescriptionRepository.findUndispensedByMedicalRecordId(record.getId());
            if (rxList.isEmpty())
                throw new IllegalStateException("No undispensed prescriptions to send to payment.");
            for (int i = 0; i < rxList.size(); i++) {
                com.adags.hospital.domain.medicalrecord.Prescription px = rxList.get(i);
                String drug = px.getPriceItem() != null
                        ? px.getPriceItem().getProductName()
                        : (px.getMedication() != null ? px.getMedication().getGenericName() : "Drug");
                BigDecimal price = (px.getPriceItem() != null
                        && px.getPriceItem().getPrice() != null)
                        ? px.getPriceItem().getPrice() : BigDecimal.ZERO;
                int qty = (px.getTotalQuantityToDispense() != null && px.getTotalQuantityToDispense() > 0)
                        ? px.getTotalQuantityToDispense() : 1;
                BigDecimal lineTotal = price.multiply(java.math.BigDecimal.valueOf(qty));
                String desc = drug + " — " + px.getDosage() + " " + px.getFrequency()
                        + " (×" + qty + ")";
                Invoice inv = Invoice.builder()
                        .patient(record.getPatient()).medicalRecord(record)
                        .invoiceNumber(prefix + "-" + System.currentTimeMillis() + "-" + i)
                        .invoiceDate(LocalDateTime.now()).status(InvoiceStatus.ISSUED)
                        .notes("Prescription: " + drug + " — " + visitDate)
                        .subtotal(lineTotal).totalAmount(lineTotal).build();
                inv.getLineItems().add(InvoiceLineItem.builder()
                        .invoice(inv).description(desc).category(cat)
                        .quantity(qty).unitPrice(price).lineTotal(lineTotal).build());
                created.add(invoiceRepository.save(inv));

                // Gate: prescription must not be visible to pharmacist until payment is confirmed
                px.setPharmacyStatus(com.adags.hospital.domain.medicalrecord.PrescriptionPharmacyStatus.AWAITING_PAYMENT);
                prescriptionRepository.save(px);
            }
        }
        return created;
    }

    // -----------------------------------------------------------------------
    // (REMOVED) saveAndSendToPayment — replaced by addPrescription/addLabRequest + sendXxxToPayment
    // -----------------------------------------------------------------------

    // kept as a stub so the old DoctorApiController.java endpoint compile fails loudly
    // if the endpoint is not also removed.
    @Transactional
    public List<Invoice> saveAndSendToPayment(UUID appointmentId,
                                              Staff doctor,
                                              ConsultationFormRequest form,
                                              String type) {
        throw new UnsupportedOperationException(
                "saveAndSendToPayment removed — use addPrescription/addLabRequest + sendXxxToPayment instead.");
    }

    // -----------------------------------------------------------------------
    // Get existing invoices for a consultation (keyed by category)
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Invoice> getConsultationInvoices(UUID appointmentId) {
        Optional<MedicalRecord> opt = medicalRecordRepository.findByAppointmentId(appointmentId);
        if (opt.isEmpty()) return Map.of();
        List<Invoice> invoices = invoiceRepository.findByMedicalRecordId(opt.get().getId());
        Map<String, Invoice> result = new HashMap<>();
        for (Invoice inv : invoices) {
            if (inv.getStatus() == InvoiceStatus.VOIDED) continue;
            for (InvoiceLineItem li : inv.getLineItems()) {
                if (li.getCategory() == LineItemCategory.PHARMACY) {
                    result.put("PHARMACY", inv);
                    break;
                } else if (li.getCategory() == LineItemCategory.LAB) {
                    result.put("LAB", inv);
                    break;
                }
            }
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Doctor Results / history page data
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<LabRequest> getDoctorLabResults(UUID doctorId) {
        List<LabRequest> results = labRequestRepository.findCompletedByDoctorIdWithResults(doctorId);
        results.sort(java.util.Comparator.comparing(
                LabRequest::getRequestedAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        return results;
    }
}
