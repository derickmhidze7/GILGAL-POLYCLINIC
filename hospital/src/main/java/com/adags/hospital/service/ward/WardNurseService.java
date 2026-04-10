package com.adags.hospital.service.ward;

import com.adags.hospital.domain.billing.Invoice;
import com.adags.hospital.domain.billing.InvoiceLineItem;
import com.adags.hospital.domain.billing.InvoiceStatus;
import com.adags.hospital.domain.billing.LineItemCategory;
import com.adags.hospital.domain.billing.Payment;
import com.adags.hospital.domain.patient.Patient;
import com.adags.hospital.domain.surgery.SurgeryItemList;
import com.adags.hospital.domain.surgery.SurgeryItemListType;
import com.adags.hospital.domain.surgery.SurgeryOrder;
import com.adags.hospital.domain.surgery.SurgeryStatus;
import com.adags.hospital.repository.surgery.SurgeryItemListRepository;
import com.adags.hospital.repository.surgery.SurgeryOrderRepository;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.pricing.ServicePriceItem;
import com.adags.hospital.domain.ward.WardOption;
import com.adags.hospital.domain.ward.WardPatientAssignment;
import com.adags.hospital.domain.ward.WardPatientStatus;
import com.adags.hospital.domain.ward.VitalSigns;
import com.adags.hospital.domain.ward.MedicationAdministrationRecord;
import com.adags.hospital.domain.ward.WoundCareNote;
import com.adags.hospital.domain.ward.WardLabRequest;
import com.adags.hospital.domain.ward.WardPrescription;
import com.adags.hospital.repository.billing.InvoiceRepository;
import com.adags.hospital.repository.ward.WardLabRequestRepository;
import com.adags.hospital.repository.patient.PatientRepository;
import com.adags.hospital.repository.pricing.ServicePriceItemRepository;
import com.adags.hospital.repository.staff.StaffRepository;
import com.adags.hospital.repository.ward.WardPatientAssignmentRepository;
import com.adags.hospital.repository.ward.VitalSignsRepository;
import com.adags.hospital.repository.ward.MedicationAdministrationRecordRepository;
import com.adags.hospital.repository.ward.WoundCareNoteRepository;
import com.adags.hospital.repository.ward.WardPrescriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class WardNurseService {

    private final WardPatientAssignmentRepository assignmentRepository;
    private final VitalSignsRepository vitalSignsRepository;
    private final MedicationAdministrationRecordRepository marRepository;
    private final WoundCareNoteRepository woundCareNoteRepository;
    private final PatientRepository patientRepository;
    private final StaffRepository staffRepository;
    private final InvoiceRepository invoiceRepository;
    private final WardPrescriptionRepository wardPrescriptionRepository;
    private final WardLabRequestRepository wardLabRequestRepository;
    private final ServicePriceItemRepository priceItemRepository;
    private final SurgeryOrderRepository surgeryOrderRepository;
    private final SurgeryItemListRepository surgeryItemListRepository;

    /** All currently admitted / non-discharged patients. */
    public List<WardPatientAssignment> getActiveAssignments() {
        return assignmentRepository.findActiveAssignments(List.of(WardPatientStatus.DISCHARGED, WardPatientStatus.TRANSFERRED));
    }

    public WardPatientAssignment getAssignment(UUID id) {
        return assignmentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("Ward assignment not found: " + id));
    }

    /** Admit a patient to the ward.
     *  @param expectedAdmissionDays if non-null, sets dischargeDate = admitDate + N days
     *         (overwritten with the real timestamp when patient is actually discharged). */
    @Transactional
    public WardPatientAssignment admitPatient(UUID patientId, UUID doctorId, UUID nurseId,
                                              String ward, Integer expectedAdmissionDays,
                                              String notes) {
        // Guard: prevent double-admission
        List<WardPatientStatus> activeStatuses = List.of(
                WardPatientStatus.ADMITTED, WardPatientStatus.STABLE, WardPatientStatus.CRITICAL);
        if (assignmentRepository.existsByPatientIdAndStatusIn(patientId, activeStatuses)) {
            throw new IllegalStateException("Patient is already admitted to the ward.");
        }

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + patientId));
        Staff doctor = doctorId != null ? staffRepository.findById(doctorId).orElse(null) : null;
        Staff nurse  = nurseId  != null ? staffRepository.findById(nurseId).orElse(null)  : null;

        LocalDateTime admitDate = LocalDateTime.now();
        LocalDateTime expectedDischarge = (expectedAdmissionDays != null && expectedAdmissionDays > 0)
                ? admitDate.plusDays(expectedAdmissionDays) : null;

        WardPatientAssignment assignment = WardPatientAssignment.builder()
                .patient(patient)
                .assignedByDoctor(doctor)
                .assignedNurse(nurse)
                .ward(ward)
                .admissionNotes(notes)
                .status(WardPatientStatus.ADMITTED)
                .admitDate(admitDate)
                .dischargeDate(expectedDischarge)
                .build();

        WardPatientAssignment saved = assignmentRepository.save(assignment);

        // Create ward invoice immediately so the receptionist can see and collect payment
        if (ward != null && !ward.isBlank()) {
            BigDecimal rate = WardOption.getRateForWard(ward);
            if (rate != null && rate.compareTo(BigDecimal.ZERO) > 0) {
                int days = (expectedAdmissionDays != null && expectedAdmissionDays > 0) ? expectedAdmissionDays : 1;
                BigDecimal total = rate.multiply(BigDecimal.valueOf(days));
                String invNum = "WRD-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
                Invoice invoice = Invoice.builder()
                        .patient(patient)
                        .invoiceNumber(invNum)
                        .invoiceDate(LocalDateTime.now())
                        .dueDate(LocalDate.now().plusDays(1))
                        .status(InvoiceStatus.ISSUED)
                        .subtotal(total)
                        .totalAmount(total)
                        .notes("Ward admission: " + ward + " @ TZSh " + rate.toPlainString() + "/day")
                        .build();
                InvoiceLineItem lineItem = InvoiceLineItem.builder()
                        .invoice(invoice)
                        .description(ward + " \u2014 Ward Admission Fee (" + days + " day(s))")
                        .category(LineItemCategory.BED)
                        .quantity(days)
                        .unitPrice(rate)
                        .lineTotal(total)
                        .build();
                invoice.getLineItems().add(lineItem);
                Invoice savedInv = invoiceRepository.save(invoice);
                saved.setWardDailyRate(rate);
                saved.setWardInvoiceId(savedInv.getId());
                saved = assignmentRepository.save(saved);
            }
        }

        return saved;
    }

    /** Update ward assignment status; on discharge auto-updates the ward invoice quantity to actual days. */
    @Transactional
    public WardPatientAssignment updateStatus(UUID assignmentId, WardPatientStatus newStatus) {
        WardPatientAssignment assignment = getAssignment(assignmentId);
        assignment.setStatus(newStatus);
        if (newStatus == WardPatientStatus.DISCHARGED || newStatus == WardPatientStatus.TRANSFERRED) {
            LocalDateTime dischargeTime = LocalDateTime.now();
            assignment.setDischargeDate(dischargeTime);
            int days = computeDaysStayed(assignment.getAdmitDate(), dischargeTime);
            applyDaysToWardInvoice(assignment, days);
        }
        if (newStatus == WardPatientStatus.DISCHARGED) {
            com.adags.hospital.domain.patient.Patient patient = assignment.getPatient();
            patient.setActive(false);
            patientRepository.save(patient);
        }
        return assignmentRepository.save(assignment);
    }

    /**
     * Updates the ward invoice for an ACTIVE (still admitted) patient.
     * Always updates total regardless of current status — only skips VOIDED/TERMINATED.
     * Never marks the invoice as PAID while the patient is still admitted,
     * because future days will continue to accrue.
     */
    @Transactional
    void updateActiveWardInvoice(WardPatientAssignment assignment, int days) {
        if (assignment.getWardInvoiceId() == null || assignment.getWardDailyRate() == null) return;
        invoiceRepository.findById(assignment.getWardInvoiceId()).ifPresent(inv -> {
            if (inv.getStatus() == InvoiceStatus.VOIDED
                    || inv.getStatus() == InvoiceStatus.TERMINATED) return;
            BigDecimal rate     = assignment.getWardDailyRate();
            BigDecimal newTotal = rate.multiply(BigDecimal.valueOf(days));
            if (!inv.getLineItems().isEmpty()) {
                InvoiceLineItem li = inv.getLineItems().get(0);
                li.setQuantity(days);
                li.setDescription(assignment.getWard() + " \u2014 Ward Stay (" + days + " day(s))");
                li.setLineTotal(newTotal);
            }
            inv.setSubtotal(newTotal);
            inv.setTotalAmount(newTotal);
            // Recalculate status — never PAID while patient is admitted (future days will accrue)
            BigDecimal totalPaid = inv.getPayments().stream()
                    .map(Payment::getAmountPaid)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
                inv.setStatus(InvoiceStatus.PARTIALLY_PAID);
            } else {
                inv.setStatus(InvoiceStatus.ISSUED);
            }
            invoiceRepository.save(inv);
        });
    }

    /**
     * Updates the ward invoice line item quantity and total to reflect {@code days} days stayed.
     * Used on discharge/transfer to finalise the invoice. Recalculates status based on payments.
     */
    void applyDaysToWardInvoice(WardPatientAssignment assignment, int days) {
        if (assignment.getWardInvoiceId() == null || assignment.getWardDailyRate() == null) return;
        invoiceRepository.findById(assignment.getWardInvoiceId()).ifPresent(inv -> {
            if (inv.getStatus() == InvoiceStatus.VOIDED
                    || inv.getStatus() == InvoiceStatus.TERMINATED) return;
            BigDecimal rate  = assignment.getWardDailyRate();
            BigDecimal total = rate.multiply(BigDecimal.valueOf(days));
            if (!inv.getLineItems().isEmpty()) {
                InvoiceLineItem li = inv.getLineItems().get(0);
                li.setQuantity(days);
                li.setDescription(assignment.getWard() + " \u2014 Ward Stay (" + days + " day(s))");
                li.setLineTotal(total);
            }
            inv.setSubtotal(total);
            inv.setTotalAmount(total);
            // Recalculate final status based on payments
            BigDecimal totalPaid = inv.getPayments().stream()
                    .map(Payment::getAmountPaid)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalPaid.compareTo(total) >= 0) {
                inv.setStatus(InvoiceStatus.PAID);
            } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
                inv.setStatus(InvoiceStatus.PARTIALLY_PAID);
            } else {
                inv.setStatus(InvoiceStatus.ISSUED);
            }
            invoiceRepository.save(inv);
        });
    }

    /**
     * Runs at 10:00 AM every day.
     * Updates the ward invoice for every active patient with the current days-stayed count.
     * ISSUED and PARTIALLY_PAID invoices are updated; PAID/VOIDED/TERMINATED are skipped.
     */
    @Scheduled(cron = "0 0 10 * * *")
    @Transactional
    public void updateDailyWardCharges() {
        List<WardPatientAssignment> assignments = assignmentRepository.findActiveAssignments(
                java.util.List.of(com.adags.hospital.domain.ward.WardPatientStatus.DISCHARGED));
        int updated = 0;
        for (WardPatientAssignment a : assignments) {
            if (a.getWardInvoiceId() == null || a.getWardDailyRate() == null) continue;
            int days = computeDaysStayed(a.getAdmitDate(), LocalDateTime.now());
            updateActiveWardInvoice(a, days);
            updated++;
        }
        log.info("Ward daily billing: updated {} active patient invoice(s).", updated);
    }

    /** Record vital signs for a ward patient. */
    @Transactional
    public VitalSigns recordVitals(UUID assignmentId, VitalSigns vitals) {
        WardPatientAssignment assignment = getAssignment(assignmentId);
        vitals.setWardAssignment(assignment);
        vitals.setPatient(assignment.getPatient());
        if (vitals.getRecordedAt() == null) vitals.setRecordedAt(LocalDateTime.now());

        // Auto-flag alerts
        boolean alert = false;
        StringBuilder alertMsg = new StringBuilder();
        if (vitals.getBpSystolic()    != null && (vitals.getBpSystolic() > 160 || vitals.getBpSystolic() < 90)) {
            alert = true; alertMsg.append("BP out of range. ");
        }
        if (vitals.getPulseRate()      != null && (vitals.getPulseRate() > 120 || vitals.getPulseRate() < 50)) {
            alert = true; alertMsg.append("Pulse out of range. ");
        }
        if (vitals.getSpo2()           != null && vitals.getSpo2() < 92) {
            alert = true; alertMsg.append("SpO2 low. ");
        }
        if (vitals.getPainScore()      != null && vitals.getPainScore() >= 8) {
            alert = true; alertMsg.append("High pain score. ");
        }
        vitals.setHasAlerts(alert);
        if (alert) vitals.setAlertDetails(alertMsg.toString().trim());

        return vitalSignsRepository.save(vitals);
    }

    /** Get last 5 vital sign readings for a ward assignment. */
    public List<VitalSigns> getRecentVitals(UUID assignmentId) {
        return vitalSignsRepository.findTop5ByWardAssignmentIdOrderByRecordedAtDesc(assignmentId);
    }

    /** All ward assignments handled by the given nurse (all statuses, newest first). */
    public List<WardPatientAssignment> getMyHistory(UUID nurseId) {
        return assignmentRepository.findByNurseIdAllHistory(nurseId);
    }

    /** Return the current active ward assignment for a given patient, or empty. */
    public java.util.Optional<WardPatientAssignment> getActiveAssignmentForPatient(UUID patientId) {
        return assignmentRepository.findFirstByPatientIdAndStatusInOrderByAdmitDateDesc(
                patientId,
                List.of(WardPatientStatus.ADMITTED, WardPatientStatus.STABLE, WardPatientStatus.CRITICAL)
        );
    }

    /** Record a medication administration, optionally deducting from a ward prescription's remaining stock. */
    @Transactional
    public MedicationAdministrationRecord recordMedication(UUID assignmentId,
            MedicationAdministrationRecord record, UUID wardPrescriptionId) {
        WardPatientAssignment assignment = getAssignment(assignmentId);
        record.setWardAssignment(assignment);
        record.setPatient(assignment.getPatient());
        if (wardPrescriptionId != null && Boolean.TRUE.equals(record.getWasGiven())) {
            wardPrescriptionRepository.findById(wardPrescriptionId).ifPresent(rx -> {
                int deduct = rx.getQuantityPerDose() != null ? rx.getQuantityPerDose() : 1;
                rx.setDispensedQuantity(rx.getDispensedQuantity() + deduct);
                wardPrescriptionRepository.save(rx);
            });
        }
        return marRepository.save(record);
    }

    /** Get medication records for a ward assignment. */
    public List<MedicationAdministrationRecord> getMedicationRecords(UUID assignmentId) {
        return marRepository.findByWardAssignmentId(assignmentId);
    }

    /** Record wound care note. */
    @Transactional
    public WoundCareNote recordWoundCare(UUID assignmentId, WoundCareNote note) {
        WardPatientAssignment assignment = getAssignment(assignmentId);
        note.setWardAssignment(assignment);
        note.setPatient(assignment.getPatient());
        if (note.getRecordedAt() == null) note.setRecordedAt(LocalDateTime.now());
        return woundCareNoteRepository.save(note);
    }

    /** Get wound care notes for a ward assignment. */
    public List<WoundCareNote> getWoundCareNotes(UUID assignmentId) {
        return woundCareNoteRepository.findTop3ByWardAssignmentIdOrderByRecordedAtDesc(assignmentId);
    }

    /** Assign a nurse to a ward patient. */
    @Transactional
    public WardPatientAssignment assignNurse(UUID assignmentId, UUID nurseId) {
        WardPatientAssignment assignment = getAssignment(assignmentId);
        Staff nurse = staffRepository.findById(nurseId)
                .orElseThrow(() -> new IllegalArgumentException("Nurse not found: " + nurseId));
        assignment.setAssignedNurse(nurse);
        return assignmentRepository.save(assignment);
    }

    /** Update bed / ward details.  Creates (or replaces) the ISSUED ward invoice for pre-payment. */
    @Transactional
    public WardPatientAssignment updateBedInfo(UUID assignmentId, String ward, String bedNumber) {
        WardPatientAssignment assignment = getAssignment(assignmentId);
        assignment.setWard(ward);
        assignment.setBedNumber(bedNumber);

        BigDecimal rate = WardOption.getRateForWard(ward);
        if (rate == null) rate = BigDecimal.ZERO; // unknown ward — zero rate
        assignment.setWardDailyRate(rate);

        // Void previous invoice if ward is being changed
        if (assignment.getWardInvoiceId() != null) {
            invoiceRepository.findById(assignment.getWardInvoiceId()).ifPresent(old -> {
                if (old.getStatus() == InvoiceStatus.ISSUED || old.getStatus() == InvoiceStatus.DRAFT) {
                    old.setStatus(InvoiceStatus.VOIDED);
                    invoiceRepository.save(old);
                }
            });
            assignment.setWardInvoiceId(null);
        }

        // Create new ISSUED ward invoice (1 day advance)
        if (rate.compareTo(BigDecimal.ZERO) > 0) {
            String invNum = "WRD-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
            BigDecimal total = rate; // 1 day
            Invoice invoice = Invoice.builder()
                    .patient(assignment.getPatient())
                    .invoiceNumber(invNum)
                    .invoiceDate(LocalDateTime.now())
                    .dueDate(LocalDate.now().plusDays(1))
                    .status(InvoiceStatus.ISSUED)
                    .subtotal(total)
                    .totalAmount(total)
                    .notes("Ward admission: " + ward + " @ TZSh " + rate.toPlainString() + "/day")
                    .build();
            InvoiceLineItem lineItem = InvoiceLineItem.builder()
                    .invoice(invoice)
                    .description(ward + " — Ward Admission Fee (1 day advance)")
                    .category(LineItemCategory.BED)
                    .quantity(1)
                    .unitPrice(rate)
                    .lineTotal(total)
                    .build();
            invoice.getLineItems().add(lineItem);
            Invoice saved = invoiceRepository.save(invoice);
            assignment.setWardInvoiceId(saved.getId());
        }

        return assignmentRepository.save(assignment);
    }

    /** Returns true if the patient currently has an active (non-discharged) ward admission. */
    /**
     * If the patient is currently in the ward (ADMITTED/STABLE/CRITICAL), discharge them.
     * Silently does nothing if no active admission exists.
     */
    @Transactional
    public void dischargeActiveWardAdmission(UUID patientId) {
        List<WardPatientStatus> active = List.of(
                WardPatientStatus.ADMITTED, WardPatientStatus.STABLE, WardPatientStatus.CRITICAL);
        assignmentRepository.findFirstByPatientIdAndStatusInOrderByAdmitDateDesc(patientId, active)
                .ifPresent(a -> updateStatus(a.getId(), WardPatientStatus.DISCHARGED));
    }

    public boolean isPatientActivelyAdmitted(UUID patientId) {
        return assignmentRepository.existsByPatientIdAndStatusIn(patientId,
                List.of(WardPatientStatus.ADMITTED, WardPatientStatus.STABLE, WardPatientStatus.CRITICAL));
    }

    /**
     * Check whether the ward invoice for this assignment has been PAID.
     * Returns true also if no invoice exists (e.g. unknown ward) so we don't block by mistake.
     */
    public boolean isWardInvoicePaid(WardPatientAssignment assignment) {
        if (assignment.getWardInvoiceId() == null) return false;
        return invoiceRepository.findById(assignment.getWardInvoiceId())
                .map(inv -> inv.getStatus() == InvoiceStatus.PAID)
                .orElse(false);
    }

    /**
     * Compute billable days stayed using 10:00 AM boundary:
     * Each calendar-day crossing of 10:00 counts as one day.
     * Minimum 1 day.
     */
    public static int computeDaysStayed(LocalDateTime admitTime, LocalDateTime dischargeTime) {
        if (admitTime == null || dischargeTime == null) return 1;
        LocalTime cutoff = LocalTime.of(10, 0);
        // "effective start date" = if admitted before 10am, charge from that day; else next day
        LocalDate admitDay    = admitTime.toLocalDate();
        LocalDate dischargeDay = dischargeTime.toLocalDate();
        // Count boundary crossings (10am)
        int days = 0;
        LocalDate d = admitDay;
        while (!d.isAfter(dischargeDay)) {
            LocalDateTime boundary = d.atTime(cutoff);
            if (!boundary.isBefore(admitTime) && boundary.isBefore(dischargeTime)) {
                days++;
            }
            d = d.plusDays(1);
        }
        return Math.max(1, days);
    }

    // ----------------------------------------------------------------
    //  View DTOs — plain records, zero JPA proxies, safe after session closes
    // ----------------------------------------------------------------
    public record VitalSignsView(
            LocalDateTime recordedAt, String recordedByName, boolean hasAlerts, String alertDetails,
            Integer bpSystolic, Integer bpDiastolic, Integer pulseRate,
            BigDecimal temperature, Integer respiratoryRate, Integer spo2, Integer painScore,
            String notes) {}

    public record MedAdminView(
            String medicationName, String doseGiven, String route,
            LocalDateTime scheduledTime, LocalDateTime administeredAt,
            boolean wasGiven, String skipReasonName, String skipNotes, String remarks,
            String administeredByName) {}

    public record WoundCareView(
            LocalDateTime recordedAt, String recordedByName,
            String woundAppearance, boolean dressingChanged, String dressingType,
            boolean signsOfInfection, String infectionDescription, String remarks) {}

    public record WardPrescriptionView(
            UUID id, String medicationName, String route, String instructions,
            Integer dosesPerDay, Integer quantityPerDose, Integer numberOfDays,
            boolean dispenseAsWhole, Integer totalQuantity, int dispensedQuantity,
            String prescribedByName, LocalDateTime prescribedAt) {
        public Integer remainingQuantity() {
            return totalQuantity != null ? totalQuantity - dispensedQuantity : null;
        }
    }

    public record AdmittedPatientSummary(
            UUID assignmentId, String patientFullName, String patientNationalId,
            String ward, String bedNumber, String statusName,
            LocalDateTime admitDate, LocalDateTime dischargeDate,
            String assignedNurseName, boolean hasAlerts) {}

    public record AdmittedPatientDetail(
            UUID assignmentId, String patientFullName, String patientNationalId,
            String ward, String bedNumber, String statusName,
            LocalDateTime admitDate, LocalDateTime dischargeDate,
            String assignedNurseName, String admissionNotes,
            List<VitalSignsView> vitals,
            List<MedAdminView> medications,
            List<WoundCareView> woundNotes,
            List<WardPrescriptionView> prescriptions,
            List<WardLabRequestView> labRequests) {}

    public record PatientBillingSummary(
            UUID assignmentId, String patientFullName, String ward, String bedNumber,
            LocalDateTime admitDate,
            BigDecimal wardBillTotal, InvoiceStatus wardBillStatus,
            BigDecimal totalUnpaid) {}

    public record SurgeryBillingSummary(
            UUID assignmentId, String patientFullName, String ward, String bedNumber,
            LocalDateTime admitDate,
            int surgeryCount,
            BigDecimal totalPreopUnpaid, BigDecimal totalPostopUnpaid, BigDecimal totalSurgeryUnpaid) {}

    public record SurgeryItemRow(
            UUID id, String itemName, int quantity,
            BigDecimal unitPrice, BigDecimal total,
            boolean dispensed, boolean isLabItem, String itemType) {}

    public record SurgeryBillSection(
            UUID surgeryOrderId, String procedureName, LocalDateTime scheduledDate,
            List<SurgeryItemRow> preopItems, List<SurgeryItemRow> postopItems) {
        public BigDecimal preopSubtotal() {
            return preopItems.stream().map(SurgeryItemRow::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        public BigDecimal postopSubtotal() {
            return postopItems.stream().map(SurgeryItemRow::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    public record PendingBillItem(String label, String invoiceNumber, BigDecimal amount, String statusDisplay) {}

    public record PatientBillDetail(
            UUID assignmentId, String patientFullName, String ward, String bedNumber,
            LocalDateTime admitDate,
            String wardInvoiceNumber, InvoiceStatus wardBillStatus,
            BigDecimal wardBillTotal, BigDecimal wardBillPaid,
            BigDecimal wardDailyRate, int daysAccrued,
            List<SurgeryBillSection> surgeries) {
        public BigDecimal wardBillRemaining() {
            return wardBillTotal.subtract(wardBillPaid).max(BigDecimal.ZERO);
        }
    }

    // ----------------------------------------------------------------
    //  Doctor view — all active admitted patients assigned by this doctor
    // ----------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<AdmittedPatientSummary> getDoctorAdmittedPatients(UUID doctorId) {
        List<WardPatientAssignment> assignments = assignmentRepository
                .findActiveAssignments(List.of(WardPatientStatus.DISCHARGED, WardPatientStatus.TRANSFERRED));
        List<AdmittedPatientSummary> result = new ArrayList<>();
        for (WardPatientAssignment wa : assignments) {
            // Only include patients admitted by this specific doctor
            if (wa.getAssignedByDoctor() == null || !wa.getAssignedByDoctor().getId().equals(doctorId)) {
                continue;
            }
            String patientFullName = wa.getPatient() != null
                    ? wa.getPatient().getFirstName() + " " + wa.getPatient().getLastName() : "";
            String patientNationalId = wa.getPatient() != null ? wa.getPatient().getNationalId() : "";
            String nurseName = wa.getAssignedNurse() != null
                    ? wa.getAssignedNurse().getFirstName() + " " + wa.getAssignedNurse().getLastName() : "—";
            boolean hasAlerts = !vitalSignsRepository.findByWardAssignmentIdAndHasAlertsTrue(wa.getId()).isEmpty();
            result.add(new AdmittedPatientSummary(
                    wa.getId(), patientFullName, patientNationalId,
                    wa.getWard(), wa.getBedNumber(), wa.getStatus().name(),
                    wa.getAdmitDate(), wa.getDischargeDate(), nurseName, hasAlerts));
        }
        return result;
    }

    // ----------------------------------------------------------------
    //  Doctor view — full care detail for one admitted patient
    // ----------------------------------------------------------------
    @Transactional(readOnly = true)
    public AdmittedPatientDetail getDoctorPatientDetail(UUID assignmentId) {
        WardPatientAssignment wa = assignmentRepository.findByIdWithDetails(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));

        String patientFullName = wa.getPatient() != null
                ? wa.getPatient().getFirstName() + " " + wa.getPatient().getLastName() : "";
        String patientNationalId = wa.getPatient() != null ? wa.getPatient().getNationalId() : "";
        String nurseName = wa.getAssignedNurse() != null
                ? wa.getAssignedNurse().getFirstName() + " " + wa.getAssignedNurse().getLastName() : "—";

        // Vitals — all records newest first
        List<VitalSigns> rawVitals = vitalSignsRepository.findByWardAssignmentIdOrderByRecordedAtDesc(wa.getId());
        List<VitalSignsView> vitalViews = new ArrayList<>();
        for (VitalSigns v : rawVitals) {
            String recBy = v.getRecordedBy() != null
                    ? v.getRecordedBy().getFirstName() + " " + v.getRecordedBy().getLastName() : "—";
            vitalViews.add(new VitalSignsView(
                    v.getRecordedAt(), recBy, Boolean.TRUE.equals(v.getHasAlerts()), v.getAlertDetails(),
                    v.getBpSystolic(), v.getBpDiastolic(), v.getPulseRate(),
                    v.getTemperature(), v.getRespiratoryRate(), v.getSpo2(), v.getPainScore(), v.getNotes()));
        }

        // Medications — all records
        List<MedicationAdministrationRecord> rawMeds = marRepository.findByWardAssignmentId(wa.getId());
        List<MedAdminView> medViews = new ArrayList<>();
        for (MedicationAdministrationRecord m : rawMeds) {
            String adminBy = m.getAdministeredBy() != null
                    ? m.getAdministeredBy().getFirstName() + " " + m.getAdministeredBy().getLastName() : "—";
            medViews.add(new MedAdminView(
                    m.getMedicationName(), m.getDoseGiven(), m.getRoute(),
                    m.getScheduledTime(), m.getAdministeredAt(),
                    Boolean.TRUE.equals(m.getWasGiven()),
                    m.getSkipReason() != null ? m.getSkipReason().name() : null,
                    m.getSkipNotes(), m.getRemarks(), adminBy));
        }

        // Wound care — all records newest first
        List<WoundCareNote> rawWounds = woundCareNoteRepository.findByWardAssignmentIdOrderByRecordedAtDesc(wa.getId());
        List<WoundCareView> woundViews = new ArrayList<>();
        for (WoundCareNote w : rawWounds) {
            String recBy = w.getRecordedBy() != null
                    ? w.getRecordedBy().getFirstName() + " " + w.getRecordedBy().getLastName() : "—";
            woundViews.add(new WoundCareView(
                    w.getRecordedAt(), recBy, w.getWoundAppearance(),
                    Boolean.TRUE.equals(w.getDressingChanged()), w.getDressingType(),
                    Boolean.TRUE.equals(w.getSignsOfInfection()), w.getInfectionDescription(), w.getRemarks()));
        }

        // Prescriptions — active only, newest first
        List<WardPrescriptionView> rxViews = wardPrescriptionRepository
                .findByWardAssignmentIdAndActiveTrueOrderByPrescribedAtDesc(wa.getId())
                .stream().map(rx -> new WardPrescriptionView(
                        rx.getId(), rx.getMedicationName(), rx.getRoute(), rx.getInstructions(),
                        rx.getDosesPerDay(), rx.getQuantityPerDose(), rx.getNumberOfDays(),
                        rx.isDispenseAsWhole(), rx.getTotalQuantity(), rx.getDispensedQuantity(),
                        rx.getPrescribedBy() != null
                                ? rx.getPrescribedBy().getFirstName() + " " + rx.getPrescribedBy().getLastName() : "—",
                        rx.getPrescribedAt()))
                .toList();

        List<WardLabRequestView> labViews = wardLabRequestRepository
                .findByWardAssignmentIdOrderByRequestedAtDesc(wa.getId())
                .stream().map(r -> new WardLabRequestView(
                        r.getId(), r.getTestName(), r.getUrgency(), r.getClinicalNotes(),
                        r.getRequestedBy() != null
                                ? r.getRequestedBy().getFirstName() + " " + r.getRequestedBy().getLastName() : "—",
                        r.getRequestedAt()))
                .toList();

        return new AdmittedPatientDetail(
                wa.getId(), patientFullName, patientNationalId,
                wa.getWard(), wa.getBedNumber(), wa.getStatus().name(),
                wa.getAdmitDate(), wa.getDischargeDate(), nurseName, wa.getAdmissionNotes(),
                vitalViews, medViews, woundViews, rxViews, labViews);
    }

    // ----------------------------------------------------------------
    //  Ward Prescriptions
    // ----------------------------------------------------------------
    @Transactional
    public WardPrescription addWardPrescription(UUID assignmentId, String medicationName,
            UUID priceItemId, String route, String instructions,
            boolean dispenseAsWhole, Integer dosesPerDay, Integer quantityPerDose,
            Integer numberOfDays, Integer totalQuantity, UUID prescribedById) {
        WardPatientAssignment wa = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));
        Staff doctor = staffRepository.findById(prescribedById)
                .orElseThrow(() -> new IllegalArgumentException("Staff not found: " + prescribedById));
        ServicePriceItem priceItem = priceItemId != null
                ? priceItemRepository.findById(priceItemId).orElse(null) : null;
        WardPrescription rx = WardPrescription.builder()
                .wardAssignment(wa)
                .medicationName(medicationName)
                .priceItem(priceItem)
                .route(route)
                .instructions(instructions)
                .dispenseAsWhole(dispenseAsWhole)
                .dosesPerDay(dosesPerDay)
                .quantityPerDose(quantityPerDose)
                .numberOfDays(numberOfDays)
                .totalQuantity(totalQuantity)
                .prescribedBy(doctor)
                .build();
        WardPrescription saved = wardPrescriptionRepository.save(rx);

        // Create PHARMACY invoice for receptionist to collect payment
        if (priceItem != null && priceItem.getPrice() != null
                && priceItem.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal price = priceItem.getPrice();
            int qty = totalQuantity != null ? totalQuantity : 1;
            BigDecimal total = price.multiply(BigDecimal.valueOf(qty));
            String invNum = "RX-" + System.currentTimeMillis() + "-W";
            Invoice invoice = Invoice.builder()
                    .patient(wa.getPatient())
                    .invoiceNumber(invNum)
                    .invoiceDate(LocalDateTime.now())
                    .dueDate(LocalDate.now().plusDays(1))
                    .status(InvoiceStatus.ISSUED)
                    .subtotal(total)
                    .totalAmount(total)
                    .notes("Ward Prescription: " + medicationName + " — " + wa.getPatient().getFirstName() + " " + wa.getPatient().getLastName())
                    .build();
            InvoiceLineItem lineItem = InvoiceLineItem.builder()
                    .invoice(invoice)
                    .description(medicationName + (dispenseAsWhole ? " (whole unit)" : " × " + qty))
                    .category(LineItemCategory.PHARMACY)
                    .quantity(qty)
                    .unitPrice(price)
                    .lineTotal(total)
                    .build();
            invoice.getLineItems().add(lineItem);
            invoiceRepository.save(invoice);
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<WardPrescriptionView> getWardPrescriptionsForNurse(UUID assignmentId) {
        return wardPrescriptionRepository
                .findByWardAssignmentIdAndActiveTrueOrderByPrescribedAtDesc(assignmentId)
                .stream().map(rx -> new WardPrescriptionView(
                        rx.getId(), rx.getMedicationName(), rx.getRoute(), rx.getInstructions(),
                        rx.getDosesPerDay(), rx.getQuantityPerDose(), rx.getNumberOfDays(),
                        rx.isDispenseAsWhole(), rx.getTotalQuantity(), rx.getDispensedQuantity(),
                        rx.getPrescribedBy() != null
                                ? rx.getPrescribedBy().getFirstName() + " " + rx.getPrescribedBy().getLastName() : "—",
                        rx.getPrescribedAt()))
                .toList();
    }

    // ----------------------------------------------------------------
    //  Ward Lab Requests
    // ----------------------------------------------------------------

    public record WardLabRequestView(
            UUID id, String testName, String urgency, String clinicalNotes,
            String requestedByName, LocalDateTime requestedAt) {}

    @Transactional
    public WardLabRequest addWardLabRequest(UUID assignmentId, UUID priceItemId,
            String urgency, String clinicalNotes, UUID requestedById) {
        WardPatientAssignment wa = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));
        Staff doctor = staffRepository.findById(requestedById)
                .orElseThrow(() -> new IllegalArgumentException("Staff not found: " + requestedById));
        ServicePriceItem priceItem = priceItemRepository.findById(priceItemId)
                .orElseThrow(() -> new IllegalArgumentException("Price item not found: " + priceItemId));

        WardLabRequest req = WardLabRequest.builder()
                .wardAssignment(wa)
                .priceItem(priceItem)
                .testName(priceItem.getProductName())
                .urgency(urgency != null ? urgency : "ROUTINE")
                .clinicalNotes(clinicalNotes)
                .requestedBy(doctor)
                .build();
        WardLabRequest saved = wardLabRequestRepository.save(req);

        // Create LAB invoice for receptionist to collect payment
        if (priceItem.getPrice() != null && priceItem.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal price = priceItem.getPrice();
            String invNum = "LAB-" + System.currentTimeMillis() + "-W";
            Invoice invoice = Invoice.builder()
                    .patient(wa.getPatient())
                    .invoiceNumber(invNum)
                    .invoiceDate(LocalDateTime.now())
                    .dueDate(LocalDate.now().plusDays(1))
                    .status(InvoiceStatus.ISSUED)
                    .subtotal(price)
                    .totalAmount(price)
                    .notes("Ward Lab Request: " + priceItem.getProductName() + " — " + wa.getPatient().getFirstName() + " " + wa.getPatient().getLastName())
                    .build();
            InvoiceLineItem lineItem = InvoiceLineItem.builder()
                    .invoice(invoice)
                    .description(priceItem.getProductName() + " (Ward Lab Request)")
                    .category(LineItemCategory.LAB)
                    .quantity(1)
                    .unitPrice(price)
                    .lineTotal(price)
                    .build();
            invoice.getLineItems().add(lineItem);
            invoiceRepository.save(invoice);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<WardLabRequestView> getWardLabRequests(UUID assignmentId) {
        return wardLabRequestRepository.findByWardAssignmentIdOrderByRequestedAtDesc(assignmentId)
                .stream().map(r -> new WardLabRequestView(
                        r.getId(), r.getTestName(), r.getUrgency(), r.getClinicalNotes(),
                        r.getRequestedBy() != null
                                ? r.getRequestedBy().getFirstName() + " " + r.getRequestedBy().getLastName() : "—",
                        r.getRequestedAt()))
                .toList();
    }

    // ----------------------------------------------------------------
    //  Billing overview — nurse read-only view
    // ----------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PatientBillingSummary> getBillingOverview() {
        List<WardPatientStatus> active = List.of(
                WardPatientStatus.ADMITTED, WardPatientStatus.STABLE, WardPatientStatus.CRITICAL);
        return assignmentRepository.findByStatusIn(active).stream().map(wa -> {
            BigDecimal[] wardTotal = {BigDecimal.ZERO};
            InvoiceStatus[] wardStatus = {null};
            BigDecimal[] unpaid = {BigDecimal.ZERO};

            if (wa.getWardInvoiceId() != null) {
                invoiceRepository.findById(wa.getWardInvoiceId()).ifPresent(inv -> {
                    wardTotal[0] = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
                    wardStatus[0] = inv.getStatus();
                    if (inv.getStatus() != InvoiceStatus.VOIDED
                            && inv.getStatus() != InvoiceStatus.TERMINATED) {
                        BigDecimal paid = inv.getPayments().stream()
                                .map(Payment::getAmountPaid)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        BigDecimal balance = wardTotal[0].subtract(paid).max(BigDecimal.ZERO);
                        unpaid[0] = unpaid[0].add(balance);
                    }
                });
            }

            List<SurgeryOrder> surgeries = surgeryOrderRepository
                    .findByPatientIdOrderByScheduledDateDesc(wa.getPatient().getId())
                    .stream()
                    .filter(s -> s.getStatus() != SurgeryStatus.CANCELLED
                            && s.getScheduledDate() != null
                            && !s.getScheduledDate().isBefore(wa.getAdmitDate()))
                    .toList();

            for (SurgeryOrder so : surgeries) {
                if (!so.isSentForPayment()) {
                    List<SurgeryItemList> items = surgeryItemListRepository.findBySurgeryOrderId(so.getId());
                    BigDecimal itemsTotal = items.stream()
                            .map(i -> BigDecimal.valueOf(i.getQuantity())
                                    .multiply(i.getPrice() != null ? i.getPrice() : BigDecimal.ZERO))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    unpaid[0] = unpaid[0].add(itemsTotal);
                }
            }

            String fullName = wa.getPatient().getFirstName() + " " + wa.getPatient().getLastName();
            return new PatientBillingSummary(wa.getId(), fullName, wa.getWard(),
                    wa.getBedNumber(), wa.getAdmitDate(), wardTotal[0], wardStatus[0], unpaid[0]);
        }).toList();
    }

    @Transactional(readOnly = true)
    public PatientBillDetail getPatientBillDetail(UUID assignmentId) {
        WardPatientAssignment wa = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));

        String invNum = null;
        InvoiceStatus invStatus = null;
        BigDecimal invTotal = BigDecimal.ZERO;
        BigDecimal invPaid = BigDecimal.ZERO;

        if (wa.getWardInvoiceId() != null) {
            Invoice inv = invoiceRepository.findById(wa.getWardInvoiceId()).orElse(null);
            if (inv != null) {
                invNum = inv.getInvoiceNumber();
                invStatus = inv.getStatus();
                invTotal = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
                invPaid = inv.getPayments().stream()
                        .map(Payment::getAmountPaid)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
        }

        List<SurgeryBillSection> sections = surgeryOrderRepository
                .findByPatientIdOrderByScheduledDateDesc(wa.getPatient().getId())
                .stream()
                .filter(s -> s.getStatus() != SurgeryStatus.CANCELLED
                        && s.getScheduledDate() != null
                        && !s.getScheduledDate().isBefore(wa.getAdmitDate()))
                .map(so -> {
                    List<SurgeryItemList> all = surgeryItemListRepository.findBySurgeryOrderId(so.getId());
                    List<SurgeryItemRow> preop = all.stream()
                            .filter(i -> i.getItemType() == SurgeryItemListType.PRE_OP)
                            .map(i -> toSurgeryItemRow(i, "PRE_OP")).toList();
                    List<SurgeryItemRow> postop = all.stream()
                            .filter(i -> i.getItemType() == SurgeryItemListType.POST_OP)
                            .map(i -> toSurgeryItemRow(i, "POST_OP")).toList();
                    return new SurgeryBillSection(so.getId(), so.getProcedureName(),
                            so.getScheduledDate(), preop, postop);
                }).toList();

        String fullName = wa.getPatient().getFirstName() + " " + wa.getPatient().getLastName();
        BigDecimal dailyRate = wa.getWardDailyRate() != null ? wa.getWardDailyRate() : BigDecimal.ZERO;
        int daysAccrued = computeDaysStayed(wa.getAdmitDate(), LocalDateTime.now());
        return new PatientBillDetail(wa.getId(), fullName, wa.getWard(), wa.getBedNumber(),
                wa.getAdmitDate(), invNum, invStatus, invTotal, invPaid, dailyRate, daysAccrued, sections);
    }

    private SurgeryItemRow toSurgeryItemRow(SurgeryItemList i, String type) {
        BigDecimal price = i.getPrice() != null ? i.getPrice() : BigDecimal.ZERO;
        return new SurgeryItemRow(i.getId(), i.getItemName(), i.getQuantity(), price,
                BigDecimal.valueOf(i.getQuantity()).multiply(price),
                i.isDispensed(), i.isLabItem(), type);
    }

    @Transactional(readOnly = true)
    public List<SurgeryBillingSummary> getSurgeryBillingOverview() {
        List<WardPatientStatus> active = List.of(
                WardPatientStatus.ADMITTED, WardPatientStatus.STABLE, WardPatientStatus.CRITICAL);
        List<SurgeryBillingSummary> result = new ArrayList<>();

        for (WardPatientAssignment wa : assignmentRepository.findByStatusIn(active)) {
            List<SurgeryOrder> unpaidSurgeries = surgeryOrderRepository
                    .findByPatientIdOrderByScheduledDateDesc(wa.getPatient().getId())
                    .stream()
                    .filter(s -> s.getStatus() != SurgeryStatus.CANCELLED
                            && s.getScheduledDate() != null
                            && !s.getScheduledDate().isBefore(wa.getAdmitDate())
                            && !s.isSentForPayment())
                    .toList();

            if (unpaidSurgeries.isEmpty()) continue;

            BigDecimal preopTotal = BigDecimal.ZERO;
            BigDecimal postopTotal = BigDecimal.ZERO;

            for (SurgeryOrder so : unpaidSurgeries) {
                for (SurgeryItemList item : surgeryItemListRepository.findBySurgeryOrderId(so.getId())) {
                    BigDecimal lineTotal = BigDecimal.valueOf(item.getQuantity())
                            .multiply(item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO);
                    if (item.getItemType() == SurgeryItemListType.PRE_OP) {
                        preopTotal = preopTotal.add(lineTotal);
                    } else if (item.getItemType() == SurgeryItemListType.POST_OP) {
                        postopTotal = postopTotal.add(lineTotal);
                    }
                }
            }

            if (preopTotal.compareTo(BigDecimal.ZERO) == 0
                    && postopTotal.compareTo(BigDecimal.ZERO) == 0) continue;

            String fullName = wa.getPatient().getFirstName() + " " + wa.getPatient().getLastName();
            result.add(new SurgeryBillingSummary(
                    wa.getId(), fullName, wa.getWard(), wa.getBedNumber(), wa.getAdmitDate(),
                    unpaidSurgeries.size(), preopTotal, postopTotal,
                    preopTotal.add(postopTotal)));
        }
        return result;
    }

    // ----------------------------------------------------------------
    //  Pending bills check — used by doctor discharge guard
    // ----------------------------------------------------------------

    private static final Set<InvoiceStatus> UNPAID_STATUSES = Set.of(
            InvoiceStatus.DRAFT, InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID);

    private List<PendingBillItem> buildPendingBillsList(WardPatientAssignment wa) {
        List<PendingBillItem> pending = new ArrayList<>();

        // Ward invoice check
        if (wa.getWardInvoiceId() != null) {
            invoiceRepository.findById(wa.getWardInvoiceId()).ifPresent(inv -> {
                if (UNPAID_STATUSES.contains(inv.getStatus())) {
                    pending.add(new PendingBillItem("Ward Bill",
                            inv.getInvoiceNumber(),
                            inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO,
                            inv.getStatus().name()));
                }
            });
        }

        // Surgery checks (scoped to current admission)
        surgeryOrderRepository.findByPatientIdOrderByScheduledDateDesc(wa.getPatient().getId())
                .stream()
                .filter(s -> s.getStatus() != SurgeryStatus.CANCELLED
                        && s.getScheduledDate() != null
                        && !s.getScheduledDate().isBefore(wa.getAdmitDate()))
                .forEach(so -> {
                    // Surgery invoice unpaid
                    if (so.getSurgeryInvoiceId() != null) {
                        invoiceRepository.findById(so.getSurgeryInvoiceId()).ifPresent(inv -> {
                            if (UNPAID_STATUSES.contains(inv.getStatus())) {
                                pending.add(new PendingBillItem(
                                        "Surgery Invoice \u2014 " + (so.getProcedureName() != null ? so.getProcedureName() : "Unnamed"),
                                        inv.getInvoiceNumber(),
                                        inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO,
                                        inv.getStatus().name()));
                            }
                        });
                    }
                    // Surgery items not yet sent for payment
                    if (!so.isSentForPayment()) {
                        List<SurgeryItemList> items = surgeryItemListRepository.findBySurgeryOrderId(so.getId());
                        if (!items.isEmpty()) {
                            BigDecimal total = items.stream()
                                    .map(i -> BigDecimal.valueOf(i.getQuantity())
                                            .multiply(i.getPrice() != null ? i.getPrice() : BigDecimal.ZERO))
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                            if (total.compareTo(BigDecimal.ZERO) > 0) {
                                pending.add(new PendingBillItem(
                                        "Surgery Items \u2014 " + (so.getProcedureName() != null ? so.getProcedureName() : "Unnamed"),
                                        null, total, "NOT_SENT_FOR_PAYMENT"));
                            }
                        }
                    }
                });

        return pending;
    }

    @Transactional(readOnly = true)
    public List<PendingBillItem> getPendingBillsForAssignment(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .map(this::buildPendingBillsList)
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<PendingBillItem> getPendingBillsForPatient(UUID patientId) {
        List<WardPatientStatus> active = List.of(
                WardPatientStatus.ADMITTED, WardPatientStatus.STABLE, WardPatientStatus.CRITICAL);
        return assignmentRepository.findByStatusIn(active).stream()
                .filter(wa -> wa.getPatient().getId().equals(patientId))
                .findFirst()
                .map(this::buildPendingBillsList)
                .orElse(List.of());
    }
}
