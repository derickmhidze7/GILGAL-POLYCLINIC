package com.adags.hospital.service.surgery;

import com.adags.hospital.domain.billing.Invoice;
import com.adags.hospital.domain.billing.InvoiceLineItem;
import com.adags.hospital.domain.billing.InvoiceStatus;
import com.adags.hospital.domain.billing.LineItemCategory;
import com.adags.hospital.domain.lab.LabRequest;
import com.adags.hospital.domain.medicalrecord.MedicalRecord;
import com.adags.hospital.domain.pricing.ServicePriceItem;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.surgery.*;
import com.adags.hospital.domain.ward.WardPatientAssignment;
import com.adags.hospital.domain.user.Role;
import com.adags.hospital.dto.consultation.SurgeryOrderRequest;
import com.adags.hospital.exception.ResourceNotFoundException;
import com.adags.hospital.domain.pharmacy.InventoryItem;
import com.adags.hospital.domain.pharmacy.Medication;
import com.adags.hospital.repository.billing.InvoiceRepository;
import com.adags.hospital.repository.lab.LabRequestRepository;
import com.adags.hospital.repository.medicalrecord.MedicalRecordRepository;
import com.adags.hospital.repository.patient.PatientRepository;
import com.adags.hospital.repository.pharmacy.InventoryRepository;
import com.adags.hospital.repository.pharmacy.MedicationRepository;
import com.adags.hospital.repository.pricing.ServicePriceItemRepository;
import com.adags.hospital.repository.staff.StaffRepository;
import com.adags.hospital.repository.surgery.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SurgeryService {

    private final SurgeryOrderRepository       surgeryOrderRepository;
    private final SurgeryItemListRepository    surgeryItemListRepository;
    private final SurgeryPostopCareRepository  postopCareRepository;
    private final ServicePriceItemRepository   servicePriceItemRepository;
    private final MedicalRecordRepository      medicalRecordRepository;
    private final PatientRepository            patientRepository;
    private final StaffRepository              staffRepository;
    private final InvoiceRepository            invoiceRepository;
    private final MedicationRepository         medicationRepository;
    private final InventoryRepository          inventoryRepository;
    private final LabRequestRepository         labRequestRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // DTOs (plain records — safe with open-in-view=false)
    // ─────────────────────────────────────────────────────────────────────────

    public record SurgeryOrderView(
            UUID id,
            String patientName,
            String patientMrn,
            String procedureName,
            String surgeryType,
            String urgency,
            String anesthesiaType,
            LocalDateTime scheduledDate,
            String operatingTheater,
            String status,
            boolean consentObtained,
            String preopNotes,
            BigDecimal price,
            LocalDateTime createdAt
    ) {}

    public record SurgeryDetailView(
            UUID id,
            UUID patientId,
            String patientName,
            String patientMrn,
            String patientDob,
            String doctorName,
            String procedureName,
            String surgeryType,
            String urgency,
            String anesthesiaType,
            LocalDateTime scheduledDate,
            String operatingTheater,
            String status,
            boolean consentObtained,
            String preopNotes,
            String postopNotes,
            BigDecimal price,
            Integer estimatedDurationMinutes,
            List<AssignedNurseView> nurses,
            List<ItemView> preopItems,
            List<ItemView> postopItems,
            IntraopView intraoperative,
            List<PostopCareView> postopCareRecords,
            LocalDateTime createdAt,
            String consentDocumentPath,
            boolean invoicePaid,
            boolean sentForPayment,
            LocalDateTime sentForPaymentAt
    ) {}

    public record AssignedNurseView(UUID nurseId, String nurseName, String role) {}

    public record ItemView(UUID id, String itemName, int quantity, BigDecimal price,
                           boolean dispensed, String pharmacyNotes, String itemType,
                           String route, String instructions,
                           boolean dispenseAsWhole,
                           Integer dosesPerDay, Integer quantityPerDose, Integer numberOfDays,
                           boolean paid,
                           boolean isLabItem) {}

    public record IntraopView(
            UUID id,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String leadSurgeon,
            String anesthesiologist,
            Integer bloodLossMl,
            Integer fluidsGivenMl,
            String complications,
            String intraopNotes,
            String anesthesiaNotes
    ) {}

    public record PostopCareView(
            UUID id,
            LocalDateTime recordedAt,
            String nurseName,
            String consciousnessLevel,
            String bloodPressure,
            Integer pulseRate,
            BigDecimal spo2,
            Integer painScore,
            BigDecimal temperature,
            String recoveryNotes,
            String nextStep,
            String recordedByRole   // e.g. "DOCTOR", "NURSE", "WARD_NURSE"
    ) {}

    public record NurseAvailable(UUID id, String name, String role) {}

    public record SurgeryProcedure(UUID id, String code, String name, String category, BigDecimal price) {}

    // ─────────────────────────────────────────────────────────────────────────
    // Surgery catalog search
    // ─────────────────────────────────────────────────────────────────────────

    public List<SurgeryProcedure> searchSurgeryProcedures(String keyword) {
        List<ServicePriceItem> items;
        if (keyword == null || keyword.isBlank()) {
            items = servicePriceItemRepository.findAllSurgeryAndProcedure();
        } else {
            items = servicePriceItemRepository.searchSurgeryAndProcedure(keyword.trim());
        }
        return items.stream()
                .map(i -> new SurgeryProcedure(i.getId(), i.getProductCode(), i.getProductName(),
                        i.getCategory(), i.getPrice()))
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Available nurses
    // ─────────────────────────────────────────────────────────────────────────

    public List<NurseAvailable> getAvailableNurses() {
        List<Staff> nurses = new ArrayList<>(staffRepository.findByStaffRoleAndActiveTrue(Role.WARD_NURSE));
        nurses.addAll(staffRepository.findByStaffRoleAndActiveTrue(Role.NURSE));
        return nurses.stream()
                .map(n -> new NurseAvailable(n.getId(),
                        n.getFirstName() + " " + n.getLastName(),
                        n.getStaffRole().name()))
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pharmacy item search for surgery item forms
    // ─────────────────────────────────────────────────────────────────────────

    public record PharmacyItemSuggestion(
            String name,
            String category,
            String form,
            String strength,
            BigDecimal unitCost
    ) {}

    public record LabItemSuggestion(
            UUID id,
            String name,
            String classification,
            String productCode,
            BigDecimal price
    ) {}

    public List<LabItemSuggestion> searchLabItems(String q) {
        if (q == null || q.trim().length() < 2) return List.of();
        return servicePriceItemRepository.searchLabTests(q.trim())
                .stream()
                .limit(20)
                .map(i -> new LabItemSuggestion(
                        i.getId(),
                        i.getProductName(),
                        i.getClassification() != null ? i.getClassification() : "",
                        i.getProductCode() != null ? i.getProductCode() : "",
                        i.getPrice() != null ? i.getPrice() : BigDecimal.ZERO
                ))
                .collect(Collectors.toList());
    }

    public List<PharmacyItemSuggestion> searchPharmacyItems(String q) {
        String query = (q == null) ? "" : q.trim();
        if (query.length() < 2) return List.of();
        return servicePriceItemRepository.searchPharmacyItems(query)
                .stream()
                .limit(20)
                .map(i -> new PharmacyItemSuggestion(
                        i.getProductName(),
                        i.getClassification() != null ? i.getClassification() : "PHARMACY",
                        "",
                        "",
                        i.getPrice() != null ? i.getPrice() : BigDecimal.ZERO
                ))
                .collect(Collectors.toList());
    }

    /** Returns ALL pharmacy items from the price catalogue sorted by name — used to populate dropdown menus. */
    public List<PharmacyItemSuggestion> getAllPharmacyItems() {
        return servicePriceItemRepository.findByTypeIgnoreCase("PHARMACY")
                .stream()
                .map(i -> new PharmacyItemSuggestion(
                        i.getProductName(),
                        i.getClassification() != null ? i.getClassification() : "PHARMACY",
                        "",
                        "",
                        i.getPrice() != null ? i.getPrice() : BigDecimal.ZERO
                ))
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Create surgery order from consultation
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public SurgeryOrder createSurgeryOrder(UUID patientId,
                                           UUID doctorId,
                                           UUID medicalRecordId,
                                           SurgeryOrderRequest req) {
        Staff doctor = staffRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff", "id", doctorId));

        var patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId));

        MedicalRecord record = medicalRecordId != null
                ? medicalRecordRepository.findById(medicalRecordId).orElse(null)
                : null;

        ServicePriceItem priceItem = null;
        BigDecimal price = req.getPrice();
        if (req.getServicePriceItemId() != null) {
            priceItem = servicePriceItemRepository.findById(req.getServicePriceItemId()).orElse(null);
            if (priceItem != null && price == null) {
                price = priceItem.getPrice();
            }
        }

        SurgeryOrder order = SurgeryOrder.builder()
                .patient(patient)
                .requestingDoctor(doctor)
                .medicalRecord(record)
                .servicePriceItem(priceItem)
                .procedureName(req.getProcedureName() != null ? req.getProcedureName()
                        : (priceItem != null ? priceItem.getProductName() : "Surgical Procedure"))
                .surgeryType(req.getSurgeryType() != null ? req.getSurgeryType() : "GENERAL")
                .urgency(parseUrgency(req.getUrgency()))
                .anesthesiaType(parseAnesthesia(req.getAnesthesiaType()))
                .scheduledDate(req.getScheduledDate())
                .operatingTheater(req.getOperatingTheater())
                .estimatedDurationMinutes(req.getEstimatedDurationMinutes())
                .price(price)
                .consentObtained(req.isConsentObtained())
                .preopNotes(req.getPreopNotes())
                .status(SurgeryStatus.SCHEDULED)
                .build();

        SurgeryOrder saved = surgeryOrderRepository.save(order);

        // Assign nurses
        if (req.getNurseIds() != null) {
            for (UUID nurseId : req.getNurseIds()) {
                staffRepository.findById(nurseId).ifPresent(nurse -> {
                    SurgeryAssignedNurse assignment = SurgeryAssignedNurse.builder()
                            .surgeryOrder(saved)
                            .nurse(nurse)
                            .nurseRole("SCRUB_NURSE")
                            .build();
                    saved.getAssignedNurses().add(assignment);
                });
            }
        }

        return surgeryOrderRepository.save(saved);
    }

    /**
     * Create surgery order after consultation finalization.
     * Uses the medical record to get the patient.
     */
    @Transactional
    public SurgeryOrder createSurgeryOrderFromRecord(MedicalRecord record,
                                                      Staff doctor,
                                                      SurgeryOrderRequest req) {
        ServicePriceItem priceItem = null;
        BigDecimal price = req.getPrice();
        if (req.getServicePriceItemId() != null) {
            priceItem = servicePriceItemRepository.findById(req.getServicePriceItemId()).orElse(null);
            if (priceItem != null && price == null) {
                price = priceItem.getPrice();
            }
        }

        final BigDecimal finalPrice = price;
        final ServicePriceItem finalPriceItem = priceItem;

        SurgeryOrder order = SurgeryOrder.builder()
                .patient(record.getPatient())
                .requestingDoctor(doctor)
                .medicalRecord(record)
                .servicePriceItem(finalPriceItem)
                .procedureName(req.getProcedureName() != null ? req.getProcedureName()
                        : (finalPriceItem != null ? finalPriceItem.getProductName() : "Surgical Procedure"))
                .surgeryType(req.getSurgeryType() != null ? req.getSurgeryType() : "GENERAL")
                .urgency(parseUrgency(req.getUrgency()))
                .anesthesiaType(parseAnesthesia(req.getAnesthesiaType()))
                .scheduledDate(req.getScheduledDate())
                .operatingTheater(req.getOperatingTheater())
                .estimatedDurationMinutes(req.getEstimatedDurationMinutes())
                .price(finalPrice)
                .consentObtained(req.isConsentObtained())
                .preopNotes(req.getPreopNotes())
                .status(SurgeryStatus.SCHEDULED)
                .build();

        SurgeryOrder saved = surgeryOrderRepository.save(order);

        // Assign nurses
        if (req.getNurseIds() != null) {
            List<SurgeryAssignedNurse> assignments = new ArrayList<>();
            for (UUID nurseId : req.getNurseIds()) {
                staffRepository.findById(nurseId).ifPresent(nurse -> {
                    assignments.add(SurgeryAssignedNurse.builder()
                            .surgeryOrder(saved)
                            .nurse(nurse)
                            .nurseRole("SCRUB_NURSE")
                            .build());
                });
            }
            saved.getAssignedNurses().addAll(assignments);
        }

        SurgeryOrder persisted = surgeryOrderRepository.save(saved);

        // ── Create surgery invoice (ISSUED) so patient can pay before proceeding ──
        BigDecimal invoicePrice = finalPrice != null ? finalPrice : BigDecimal.ZERO;
        String procedureLabel = persisted.getProcedureName();
        String invoiceNumber  = "SRG-" + System.currentTimeMillis();
        String notes = String.format("Surgery procedure: %s scheduled by Dr. %s %s",
                procedureLabel, doctor.getFirstName(), doctor.getLastName());

        Invoice invoice = Invoice.builder()
                .patient(record.getPatient())
                .medicalRecord(record)
                .invoiceNumber(invoiceNumber)
                .invoiceDate(LocalDateTime.now())
                .dueDate(persisted.getScheduledDate() != null
                        ? persisted.getScheduledDate().toLocalDate()
                        : java.time.LocalDate.now().plusDays(1))
                .status(InvoiceStatus.ISSUED)
                .subtotal(invoicePrice)
                .totalAmount(invoicePrice)
                .notes(notes)
                .build();

        InvoiceLineItem lineItem = InvoiceLineItem.builder()
                .invoice(invoice)
                .description(procedureLabel)
                .category(LineItemCategory.PROCEDURE)
                .quantity(1)
                .unitPrice(invoicePrice)
                .lineTotal(invoicePrice)
                .build();

        invoice.getLineItems().add(lineItem);
        Invoice savedInvoice = invoiceRepository.save(invoice);
        log.info("Surgery invoice {} created for patient {} — surgery order {}",
                invoiceNumber, record.getPatient().getNationalId(), persisted.getId());

        // Link invoice back to the surgery order so items can be billed to it later
        persisted.setSurgeryInvoiceId(savedInvoice.getId());
        return surgeryOrderRepository.save(persisted);
    }

    /**
     * Create a surgery order for a patient who is already admitted to the ward.
     * The ward admission status is NOT changed — patient stays admitted.
     */
    @Transactional
    public SurgeryOrder createSurgeryOrderFromWardAdmission(WardPatientAssignment assignment,
                                                             SurgeryOrderRequest req) {
        Staff doctor = assignment.getAssignedByDoctor();
        if (doctor == null) {
            throw new IllegalStateException("No assigning doctor found on ward assignment.");
        }

        ServicePriceItem priceItem = null;
        BigDecimal price = req.getPrice();
        if (req.getServicePriceItemId() != null) {
            priceItem = servicePriceItemRepository.findById(req.getServicePriceItemId()).orElse(null);
            if (priceItem != null && price == null) {
                price = priceItem.getPrice();
            }
        }

        final BigDecimal finalPrice = price;
        final ServicePriceItem finalPriceItem = priceItem;

        SurgeryOrder order = SurgeryOrder.builder()
                .patient(assignment.getPatient())
                .requestingDoctor(doctor)
                .medicalRecord(null)
                .servicePriceItem(finalPriceItem)
                .procedureName(req.getProcedureName() != null ? req.getProcedureName()
                        : (finalPriceItem != null ? finalPriceItem.getProductName() : "Surgical Procedure"))
                .surgeryType(req.getSurgeryType() != null ? req.getSurgeryType() : "GENERAL")
                .urgency(parseUrgency(req.getUrgency()))
                .anesthesiaType(parseAnesthesia(req.getAnesthesiaType()))
                .scheduledDate(req.getScheduledDate())
                .operatingTheater(req.getOperatingTheater())
                .estimatedDurationMinutes(req.getEstimatedDurationMinutes())
                .price(finalPrice)
                .consentObtained(req.isConsentObtained())
                .preopNotes(req.getPreopNotes())
                .status(SurgeryStatus.SCHEDULED)
                .build();

        SurgeryOrder saved = surgeryOrderRepository.save(order);

        // Assign nurses
        if (req.getNurseIds() != null) {
            List<SurgeryAssignedNurse> assignments = new ArrayList<>();
            for (UUID nurseId : req.getNurseIds()) {
                staffRepository.findById(nurseId).ifPresent(nurse ->
                        assignments.add(SurgeryAssignedNurse.builder()
                                .surgeryOrder(saved)
                                .nurse(nurse)
                                .nurseRole("SCRUB_NURSE")
                                .build()));
            }
            saved.getAssignedNurses().addAll(assignments);
        }

        SurgeryOrder persisted = surgeryOrderRepository.save(saved);

        // Create surgery invoice
        BigDecimal invoicePrice = finalPrice != null ? finalPrice : BigDecimal.ZERO;
        String invoiceNumber = "SRG-" + System.currentTimeMillis();
        String notes = String.format("Surgery procedure: %s — from ward admission, Dr. %s %s",
                persisted.getProcedureName(), doctor.getFirstName(), doctor.getLastName());

        Invoice invoice = Invoice.builder()
                .patient(assignment.getPatient())
                .medicalRecord(null)
                .invoiceNumber(invoiceNumber)
                .invoiceDate(LocalDateTime.now())
                .dueDate(persisted.getScheduledDate() != null
                        ? persisted.getScheduledDate().toLocalDate()
                        : java.time.LocalDate.now().plusDays(1))
                .status(InvoiceStatus.ISSUED)
                .subtotal(invoicePrice)
                .totalAmount(invoicePrice)
                .notes(notes)
                .build();

        InvoiceLineItem lineItem = InvoiceLineItem.builder()
                .invoice(invoice)
                .description(persisted.getProcedureName())
                .category(LineItemCategory.PROCEDURE)
                .quantity(1)
                .unitPrice(invoicePrice)
                .lineTotal(invoicePrice)
                .build();

        invoice.getLineItems().add(lineItem);
        Invoice savedInvoice = invoiceRepository.save(invoice);
        log.info("Surgery invoice {} created for ward patient {} — surgery order {}",
                invoiceNumber, assignment.getPatient().getNationalId(), persisted.getId());

        persisted.setSurgeryInvoiceId(savedInvoice.getId());
        return surgeryOrderRepository.save(persisted);
    }

    /**
     * Mark a surgery's items as sent to reception for payment collection.
     * This is an informational flag — items can still be added after sending.
     */
    @Transactional
    public SurgeryOrder sendForPayment(UUID surgeryOrderId) {
        SurgeryOrder order = surgeryOrderRepository.findByIdWithDetails(surgeryOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("SurgeryOrder", "id", surgeryOrderId));
        order.setSentForPayment(true);
        order.setSentForPaymentAt(LocalDateTime.now());
        return surgeryOrderRepository.save(order);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Doctor — list surgeries
    // ─────────────────────────────────────────────────────────────────────────

    public List<SurgeryOrderView> getDoctorSurgeries(UUID doctorId) {
        List<SurgeryOrder> orders = surgeryOrderRepository.findByDoctorWithPatient(doctorId);
        List<SurgeryOrderView> result = new ArrayList<>();
        for (SurgeryOrder o : orders) {
            result.add(toView(o));
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Doctor/Nurse — order detail
    // ─────────────────────────────────────────────────────────────────────────

    public SurgeryDetailView getSurgeryDetail(UUID surgeryOrderId) {
        SurgeryOrder order = surgeryOrderRepository.findByIdWithDetails(surgeryOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("SurgeryOrder", "id", surgeryOrderId));

        String patientName = order.getPatient().getFirstName() + " " + order.getPatient().getLastName();
        String patientMrn = order.getPatient().getNationalId() != null
                ? order.getPatient().getNationalId() : "";
        String patientDob = order.getPatient().getDateOfBirth() != null
                ? order.getPatient().getDateOfBirth().toString() : "";
        String doctorName = order.getRequestingDoctor().getFirstName()
                + " " + order.getRequestingDoctor().getLastName();

        // Nurses (loaded via lazy collection — within @Transactional)
        List<AssignedNurseView> nurses = order.getAssignedNurses().stream()
                .map(n -> new AssignedNurseView(
                        n.getNurse().getId(),
                        n.getNurse().getFirstName() + " " + n.getNurse().getLastName(),
                        n.getNurseRole()
                )).toList();

        // Items — fetched with invoice eagerly to support per-item payment status display.
        // invoicePaid is computed below and used as a fallback for legacy items (invoice IS NULL).
        boolean invoicePaidForItems = false;
        if (order.getSurgeryInvoiceId() != null) {
            invoicePaidForItems = invoiceRepository.findById(order.getSurgeryInvoiceId())
                    .map(inv -> inv.getStatus() == InvoiceStatus.PAID
                             || inv.getStatus() == InvoiceStatus.PARTIALLY_PAID)
                    .orElse(false);
        } else if (order.getMedicalRecord() != null) {
            invoicePaidForItems = invoiceRepository.findByMedicalRecordId(order.getMedicalRecord().getId())
                    .stream()
                    .anyMatch(inv -> inv.getInvoiceNumber() != null
                            && inv.getInvoiceNumber().startsWith("SRG-")
                            && (inv.getStatus() == InvoiceStatus.PAID
                                || inv.getStatus() == InvoiceStatus.PARTIALLY_PAID));
        }
        final boolean surgeryInvoicePaid = invoicePaidForItems;

        List<SurgeryItemList> allItems = surgeryItemListRepository.findBySurgeryOrderIdFetchInvoice(surgeryOrderId);
        List<ItemView> preopItems  = allItems.stream()
                .filter(i -> i.getItemType() == SurgeryItemListType.PRE_OP)
                .map(i -> toItemView(i, surgeryInvoicePaid)).toList();
        List<ItemView> postopItems = allItems.stream()
                .filter(i -> i.getItemType() == SurgeryItemListType.POST_OP)
                .map(i -> toItemView(i, surgeryInvoicePaid)).toList();

        // Intraoperative
        IntraopView intraop = null;
        if (order.getIntraoperative() != null) {
            SurgeryIntraoperative io = order.getIntraoperative();
            intraop = new IntraopView(io.getId(), io.getStartTime(), io.getEndTime(),
                    io.getLeadSurgeon(), io.getAnesthesiologist(), io.getBloodLossMl(),
                    io.getFluidsGivenMl(), io.getComplications(), io.getIntraopNotes(),
                    io.getAnesthesiaNotes());
        }

        // Postop care — loaded directly to also eager-fetch nurse names
        List<PostopCareView> postopCare = postopCareRepository.findWithNurseBySurgeryOrderId(surgeryOrderId)
                .stream()
                .map(c -> {
                    String recorderName = c.getNurse() != null
                            ? c.getNurse().getFirstName() + " " + c.getNurse().getLastName() : "-";
                    String recorderRole = c.getNurse() != null && c.getNurse().getStaffRole() != null
                            ? c.getNurse().getStaffRole().name() : "UNKNOWN";
                    return new PostopCareView(c.getId(), c.getRecordedAt(), recorderName,
                            c.getConsciousnessLevel(), c.getBloodPressure(), c.getPulseRate(),
                            c.getSpo2(), c.getPainScore(), c.getTemperature(),
                            c.getRecoveryNotes(), c.getNextStep(), recorderRole);
                }).toList();

        // Invoice paid check — reuse the value already computed for item fallback above
        boolean invoicePaid = surgeryInvoicePaid;

        return new SurgeryDetailView(
                order.getId(), order.getPatient().getId(),
                patientName, patientMrn, patientDob, doctorName,
                order.getProcedureName(), order.getSurgeryType(),
                order.getUrgency() != null ? order.getUrgency().name() : null,
                order.getAnesthesiaType() != null ? order.getAnesthesiaType().name() : null,
                order.getScheduledDate(), order.getOperatingTheater(),
                order.getStatus().name(), order.isConsentObtained(),
                order.getPreopNotes(), order.getPostopNotes(), order.getPrice(),
                order.getEstimatedDurationMinutes(), nurses,
                preopItems, postopItems, intraop, postopCare,
                order.getCreatedAt(),
                order.getConsentDocumentPath(),
                invoicePaid,
                order.isSentForPayment(),
                order.getSentForPaymentAt()
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Consent document upload
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public String uploadConsentDocument(UUID surgeryOrderId,
                                        org.springframework.web.multipart.MultipartFile file,
                                        String uploadBasePath) {
        SurgeryOrder order = surgeryOrderRepository.findById(surgeryOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("SurgeryOrder", "id", surgeryOrderId));

        try {
            String ext = ".pdf";
            java.nio.file.Path dir = java.nio.file.Paths.get(uploadBasePath, "surgery-consent", surgeryOrderId.toString());
            java.nio.file.Files.createDirectories(dir);
            String fileName = java.util.UUID.randomUUID() + ext;
            java.nio.file.Path dest = dir.resolve(fileName);
            java.nio.file.Files.copy(file.getInputStream(), dest,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            String relativePath = "surgery-consent/" + surgeryOrderId + "/" + fileName;
            order.setConsentDocumentPath(relativePath);
            surgeryOrderRepository.save(order);
            return relativePath;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to save consent document: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nurse — active surgery queue
    // ─────────────────────────────────────────────────────────────────────────

    public List<SurgeryOrderView> getActiveSurgeriesForNurse() {
        return surgeryOrderRepository.findActiveForNurses().stream()
                .map(this::toView).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nurse / Doctor — update surgery status
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public SurgeryOrder updateStatus(UUID surgeryOrderId, SurgeryStatus newStatus) {
        SurgeryOrder order = surgeryOrderRepository.findByIdWithDetails(surgeryOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("SurgeryOrder", "id", surgeryOrderId));
        order.setStatus(newStatus);
        return surgeryOrderRepository.save(order);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Intraoperative record save
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public SurgeryIntraoperative saveIntraoperative(UUID surgeryOrderId,
                                                     String leadSurgeon,
                                                     String anesthesiologist,
                                                     LocalDateTime startTime,
                                                     LocalDateTime endTime,
                                                     Integer bloodLossMl,
                                                     Integer fluidsGivenMl,
                                                     String complications,
                                                     String intraopNotes,
                                                     String anesthesiaNotes) {
        SurgeryOrder order = surgeryOrderRepository.findByIdWithDetails(surgeryOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("SurgeryOrder", "id", surgeryOrderId));

        SurgeryIntraoperative io = order.getIntraoperative();
        if (io == null) {
            io = new SurgeryIntraoperative();
            io.setSurgeryOrder(order);
            order.setIntraoperative(io);
        }

        io.setLeadSurgeon(leadSurgeon);
        io.setAnesthesiologist(anesthesiologist);
        io.setStartTime(startTime);
        io.setEndTime(endTime);
        io.setBloodLossMl(bloodLossMl);
        io.setFluidsGivenMl(fluidsGivenMl);
        io.setComplications(complications);
        io.setIntraopNotes(intraopNotes);
        io.setAnesthesiaNotes(anesthesiaNotes);

        // Mark as IN_PROGRESS when intraop record is created/updated
        if (order.getStatus() == SurgeryStatus.SCHEDULED) {
            order.setStatus(SurgeryStatus.IN_PROGRESS);
        }

        surgeryOrderRepository.save(order);
        return io;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Post-op care save (nurse)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public SurgeryPostopCare savePostopCare(UUID surgeryOrderId, UUID nurseId,
                                             String consciousnessLevel, String bloodPressure,
                                             Integer pulseRate, BigDecimal spo2,
                                             Integer painScore, BigDecimal temperature,
                                             String recoveryNotes, String nextStep) {
        SurgeryOrder order = surgeryOrderRepository.findByIdWithDetails(surgeryOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("SurgeryOrder", "id", surgeryOrderId));
        Staff nurse = nurseId != null ? staffRepository.findById(nurseId).orElse(null) : null;

        SurgeryPostopCare care = SurgeryPostopCare.builder()
                .surgeryOrder(order)
                .nurse(nurse)
                .recordedAt(LocalDateTime.now())
                .consciousnessLevel(consciousnessLevel)
                .bloodPressure(bloodPressure)
                .pulseRate(pulseRate)
                .spo2(spo2)
                .painScore(painScore)
                .temperature(temperature)
                .recoveryNotes(recoveryNotes)
                .nextStep(nextStep)
                .build();

        return postopCareRepository.save(care);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Complete surgery
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public SurgeryOrder completeSurgery(UUID surgeryOrderId, String postopNotes) {
        SurgeryOrder order = surgeryOrderRepository.findByIdWithDetails(surgeryOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("SurgeryOrder", "id", surgeryOrderId));
        order.setStatus(SurgeryStatus.COMPLETED);
        if (postopNotes != null && !postopNotes.isBlank()) {
            order.setPostopNotes(postopNotes);
        }
        return surgeryOrderRepository.save(order);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Manage item lists
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public SurgeryItemList addItem(UUID surgeryOrderId, SurgeryItemListType type,
                                    String itemName, int quantity, BigDecimal price,
                                    String route, String instructions,
                                    boolean dispenseAsWhole,
                                    Integer dosesPerDay, Integer quantityPerDose, Integer numberOfDays) {
        SurgeryOrder order = surgeryOrderRepository.findByIdWithDetails(surgeryOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("SurgeryOrder", "id", surgeryOrderId));

        BigDecimal unitPrice = price != null ? price : BigDecimal.ZERO;

        SurgeryItemList item = SurgeryItemList.builder()
                .surgeryOrder(order)
                .itemType(type)
                .itemName(itemName)
                .quantity(quantity)
                .price(unitPrice)
                .build();
        item.setRoute(route);
        item.setInstructions(instructions);
        item.setDispenseAsWhole(dispenseAsWhole);
        item.setDosesPerDay(dosesPerDay);
        item.setQuantityPerDose(quantityPerDose);
        item.setNumberOfDays(numberOfDays);
        SurgeryItemList saved = surgeryItemListRepository.save(item);

        // ── Add to the surgery invoice so the patient is billed ──────────────
        // If the main surgery fee invoice is already PAID/VOIDED (or doesn't exist),
        // create a separate supplementary ISSUED invoice for the items.
        UUID invId = order.getSurgeryInvoiceId();
        Invoice existingInv = invId != null ? invoiceRepository.findById(invId).orElse(null) : null;
        boolean useExisting = existingInv != null
                && existingInv.getStatus() != InvoiceStatus.PAID
                && existingInv.getStatus() != InvoiceStatus.VOIDED;

        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        String prefix = type == SurgeryItemListType.PRE_OP ? "Pre-op" : "Post-op";

        if (useExisting) {
            // Add to the existing unpaid invoice
            InvoiceLineItem li = InvoiceLineItem.builder()
                    .invoice(existingInv)
                    .description(prefix + ": " + itemName)
                    .category(LineItemCategory.PHARMACY)
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .build();
            existingInv.getLineItems().add(li);
            BigDecimal newSubtotal = existingInv.getLineItems().stream()
                    .map(InvoiceLineItem::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            existingInv.setSubtotal(newSubtotal);
            existingInv.setTotalAmount(newSubtotal.add(existingInv.getTaxAmount()));
            invoiceRepository.save(existingInv);
            saved.setInvoice(existingInv);
            surgeryItemListRepository.save(saved);
            log.info("Surgery item '{}' added to existing invoice {} ({})",
                    itemName, existingInv.getInvoiceNumber(), prefix);
        } else {
            // Main invoice is paid/voided or missing — create a new supplementary invoice
            // Build the line item and add it BEFORE the first save so cascade PERSIST fires.
            String invNum = "SRG-ITEMS-" + System.currentTimeMillis();
            Invoice suppInv = Invoice.builder()
                    .patient(order.getPatient())
                    .invoiceNumber(invNum)
                    .invoiceDate(LocalDateTime.now())
                    .dueDate(java.time.LocalDate.now().plusDays(7))
                    .status(InvoiceStatus.ISSUED)
                    .subtotal(lineTotal)
                    .totalAmount(lineTotal)
                    .notes("Surgery items — " + order.getProcedureName())
                    .build();
            InvoiceLineItem li = InvoiceLineItem.builder()
                    .invoice(suppInv)
                    .description(prefix + ": " + itemName)
                    .category(LineItemCategory.PHARMACY)
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .build();
            suppInv.getLineItems().add(li);
            Invoice savedSupp = invoiceRepository.save(suppInv);
            // For legacy surgeries that had no invoice, point surgeryInvoiceId at the new one
            if (invId == null) {
                order.setSurgeryInvoiceId(savedSupp.getId());
                surgeryOrderRepository.save(order);
            }
            saved.setInvoice(savedSupp);
            surgeryItemListRepository.save(saved);
            log.info("Created supplementary surgery items invoice {} (surgery order {})",
                    invNum, order.getId());
        }

        return saved;
    }

    /**
     * Adds a lab test to a surgery order's item list AND creates a proper LabRequest
     * so it flows to the lab technician queue after the surgery invoice is paid.
     * Bills the item as LineItemCategory.LAB (not PHARMACY).
     */
    @Transactional
    public SurgeryItemList addLabItem(UUID surgeryOrderId, SurgeryItemListType type,
                                      String testName, String testCode, int quantity, BigDecimal price) {
        SurgeryOrder order = surgeryOrderRepository.findByIdWithDetails(surgeryOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("SurgeryOrder", "id", surgeryOrderId));

        BigDecimal unitPrice = price != null ? price : BigDecimal.ZERO;

        // Create the LabRequest entity so it flows to the lab technician queue
        LabRequest labReq = LabRequest.builder()
                .patient(order.getPatient())
                .requestingDoctor(order.getRequestingDoctor())
                .testName(testName)
                .testCode(testCode != null && !testCode.isBlank() ? testCode : null)
                .surgeryOrder(order)
                .build();
        labRequestRepository.save(labReq);

        // Track in SurgeryItemList for display on the surgery detail page
        SurgeryItemList item = SurgeryItemList.builder()
                .surgeryOrder(order)
                .itemType(type)
                .itemName(testName)
                .quantity(quantity)
                .price(unitPrice)
                .isLabItem(true)
                .build();
        SurgeryItemList saved = surgeryItemListRepository.save(item);

        // Bill as LAB category on the surgery invoice
        UUID invId = order.getSurgeryInvoiceId();
        Invoice existingInv = invId != null ? invoiceRepository.findById(invId).orElse(null) : null;
        boolean useExisting = existingInv != null
                && existingInv.getStatus() != InvoiceStatus.PAID
                && existingInv.getStatus() != InvoiceStatus.VOIDED;

        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        String prefix = type == SurgeryItemListType.PRE_OP ? "Pre-op Lab" : "Post-op Lab";

        if (useExisting) {
            InvoiceLineItem li = InvoiceLineItem.builder()
                    .invoice(existingInv)
                    .description(prefix + ": " + testName)
                    .category(LineItemCategory.LAB)
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .build();
            existingInv.getLineItems().add(li);
            BigDecimal newSubtotal = existingInv.getLineItems().stream()
                    .map(InvoiceLineItem::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            existingInv.setSubtotal(newSubtotal);
            existingInv.setTotalAmount(newSubtotal.add(existingInv.getTaxAmount()));
            invoiceRepository.save(existingInv);
        } else {
            String invNum = "SRG-LAB-" + System.currentTimeMillis();
            Invoice suppInv = Invoice.builder()
                    .patient(order.getPatient())
                    .invoiceNumber(invNum)
                    .invoiceDate(LocalDateTime.now())
                    .dueDate(java.time.LocalDate.now().plusDays(7))
                    .status(InvoiceStatus.ISSUED)
                    .subtotal(lineTotal)
                    .totalAmount(lineTotal)
                    .notes("Surgery lab tests — " + order.getProcedureName())
                    .build();
            InvoiceLineItem li = InvoiceLineItem.builder()
                    .invoice(suppInv)
                    .description(prefix + ": " + testName)
                    .category(LineItemCategory.LAB)
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .build();
            suppInv.getLineItems().add(li);
            Invoice savedSupp = invoiceRepository.save(suppInv);
            if (invId == null) {
                order.setSurgeryInvoiceId(savedSupp.getId());
                surgeryOrderRepository.save(order);
            }
            log.info("Created supplementary surgery lab invoice {} (surgery order {})", invNum, order.getId());
        }

        log.info("Surgery lab item '{}' added (type={}, surgeryOrder={})", testName, type, surgeryOrderId);
        return saved;
    }

    @Transactional
    public void markItemDispensed(UUID itemId, String pharmacyNotes) {
        SurgeryItemList item = surgeryItemListRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("SurgeryItemList", "id", itemId));
        item.setDispensed(true);
        item.setPharmacyNotes(pharmacyNotes);
        surgeryItemListRepository.save(item);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private SurgeryOrderView toView(SurgeryOrder o) {
        String patientName = o.getPatient().getFirstName() + " " + o.getPatient().getLastName();
        String mrn = o.getPatient().getNationalId() != null
                ? o.getPatient().getNationalId() : "";
        return new SurgeryOrderView(
                o.getId(), patientName, mrn, o.getProcedureName(), o.getSurgeryType(),
                o.getUrgency() != null ? o.getUrgency().name() : null,
                o.getAnesthesiaType() != null ? o.getAnesthesiaType().name() : null,
                o.getScheduledDate(), o.getOperatingTheater(), o.getStatus().name(),
                o.isConsentObtained(), o.getPreopNotes(), o.getPrice(), o.getCreatedAt()
        );
    }

    private ItemView toItemView(SurgeryItemList i, boolean surgeryInvoicePaidFallback) {
        boolean paid;
        if (i.getInvoice() != null) {
            paid = i.getInvoice().getStatus() == InvoiceStatus.PAID
                || i.getInvoice().getStatus() == InvoiceStatus.PARTIALLY_PAID;
        } else {
            // Legacy item added before invoice FK existed — fall back to the surgery's main invoice
            paid = surgeryInvoicePaidFallback;
        }
        return new ItemView(i.getId(), i.getItemName(), i.getQuantity(), i.getPrice(),
                i.isDispensed(), i.getPharmacyNotes(), i.getItemType().name(),
                i.getRoute(), i.getInstructions(),
                i.isDispenseAsWhole(),
                i.getDosesPerDay(), i.getQuantityPerDose(), i.getNumberOfDays(),
                paid, i.isLabItem());
    }

    private SurgeryUrgency parseUrgency(String value) {
        if (value == null) return SurgeryUrgency.ELECTIVE;
        try { return SurgeryUrgency.valueOf(value.toUpperCase()); }
        catch (Exception e) { return SurgeryUrgency.ELECTIVE; }
    }

    private AnesthesiaType parseAnesthesia(String value) {
        if (value == null) return null;
        try { return AnesthesiaType.valueOf(value.toUpperCase()); }
        catch (Exception e) { return null; }
    }
}
