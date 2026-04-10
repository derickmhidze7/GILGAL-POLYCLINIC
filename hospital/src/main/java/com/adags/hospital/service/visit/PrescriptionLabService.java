package com.adags.hospital.service.visit;

import com.adags.hospital.domain.billing.*;
import com.adags.hospital.domain.lab.LabUrgency;
import com.adags.hospital.domain.medicalrecord.ConsultationStatus;
import com.adags.hospital.domain.medicalrecord.MedicalRecord;
import com.adags.hospital.domain.pricing.ServicePriceItem;
import com.adags.hospital.domain.visit.*;
import com.adags.hospital.repository.billing.InvoiceRepository;
import com.adags.hospital.repository.medicalrecord.MedicalRecordRepository;
import com.adags.hospital.repository.pricing.ServicePriceItemRepository;
import com.adags.hospital.repository.visit.VisitLabRequestRepository;
import com.adags.hospital.repository.visit.VisitPrescriptionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core service for the rebuilt Doctor Prescription and Lab Request system.
 *
 * Rules:
 *  - Items are sourced exclusively from ServicePriceItem
 *      (type='PHARMACY' for prescriptions, type='LABORATORY' for lab tests).
 *  - ADD is always allowed, even after the visit has been paid.
 *  - EDIT and DELETE are permanently locked once any invoice for the visit
 *    reaches PAID or PARTIALLY_PAID status.
 *  - Every add immediately creates an ISSUED invoice line item for the
 *    receptionist to see.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionLabService {

    private final VisitPrescriptionRepository   visitPrescriptionRepository;
    private final VisitLabRequestRepository     visitLabRequestRepository;
    private final MedicalRecordRepository       medicalRecordRepository;
    private final ServicePriceItemRepository    priceItemRepository;
    private final InvoiceRepository             invoiceRepository;

    // -----------------------------------------------------------------------
    // Payment-lock check
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} when at least one invoice for this visit has been
     * paid (fully or partially).  Edit and Delete are forbidden in that state.
     * Add is always permitted regardless.
     */
    @Transactional(readOnly = true)
    public boolean isVisitLocked(UUID medicalRecordId) {
        return invoiceRepository.findByMedicalRecordId(medicalRecordId).stream()
                .anyMatch(inv -> inv.getStatus() == InvoiceStatus.PAID
                        || inv.getStatus() == InvoiceStatus.PARTIALLY_PAID);
    }

    /**
     * Returns {@code true} when the medical record has been formally discharged
     * (consultationStatus == LOCKED).  ALL add/edit/delete operations are
     * forbidden when a visit is discharged.
     */
    @Transactional(readOnly = true)
    public boolean isVisitDischarged(UUID medicalRecordId) {
        return medicalRecordRepository.findById(medicalRecordId)
                .map(r -> r.getConsultationStatus() == ConsultationStatus.LOCKED)
                .orElse(false);
    }

    // -----------------------------------------------------------------------
    // Prescriptions
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<VisitPrescription> getPrescriptionsForVisit(UUID recordId) {
        return visitPrescriptionRepository.findByMedicalRecordIdOrderByCreatedAtAsc(recordId);
    }

    /**
     * Add a prescription item to the visit.  Always allowed unless discharged.
     * Auto-creates an ISSUED invoice line item immediately.
     */
    @Transactional
    public VisitPrescription addPrescription(UUID recordId,
                                              UUID priceItemId,
                                              String dosage,
                                              String frequency,
                                              String duration,
                                              String route,
                                              String instructions,
                                              Integer totalQuantityToDispense,
                                              UUID createdByStaffId) {

        enforceNotDischarged(recordId);
        MedicalRecord record = requireRecord(recordId);
        ServicePriceItem item = requirePriceItem(priceItemId);

        if (!"PHARMACY".equalsIgnoreCase(item.getType())) {
            throw new IllegalArgumentException(
                    "Item '" + item.getProductName() + "' is not a PHARMACY catalogue item.");
        }

        VisitPrescription px = VisitPrescription.builder()
                .medicalRecord(record)
                .priceItem(item)
                .medicationName(item.getProductName())
                .dosage(dosage)
                .frequency(frequency)
                .duration(duration)
                .route(route)
                .instructions(instructions)
                .totalQuantityToDispense(totalQuantityToDispense)
                .status(VisitPrescriptionStatus.PENDING_DISPENSING)
                .createdById(createdByStaffId)
                .build();

        px = visitPrescriptionRepository.save(px);
        log.info("Added prescription [{}] to visit [{}]", item.getProductName(), recordId);

        // Auto-create invoice line item
        addInvoiceLineItem(record, item.getProductName(), LineItemCategory.PHARMACY, item.getPrice());

        return px;
    }

    /**
     * Edit a prescription.  Rejected with {@link IllegalStateException} if
     * the visit is locked (any invoice has been paid or partially paid).
     */
    @Transactional
    public VisitPrescription updatePrescription(UUID id,
                                                 String dosage,
                                                 String frequency,
                                                 String duration,
                                                 String route,
                                                 String instructions) {

        VisitPrescription px = visitPrescriptionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Prescription not found: " + id));

        enforceNotDischarged(px.getMedicalRecord().getId());
        enforceEditLock(px.getMedicalRecord().getId());

        px.setDosage(dosage);
        px.setFrequency(frequency);
        px.setDuration(duration);
        px.setRoute(route);
        px.setInstructions(instructions);
        return visitPrescriptionRepository.save(px);
    }

    /**
     * Delete a prescription.  Rejected if the visit is locked.
     */
    @Transactional
    public void deletePrescription(UUID id) {
        VisitPrescription px = visitPrescriptionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Prescription not found: " + id));
        enforceNotDischarged(px.getMedicalRecord().getId());
        enforceEditLock(px.getMedicalRecord().getId());
        visitPrescriptionRepository.delete(px);
    }

    // -----------------------------------------------------------------------
    // Lab requests
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<VisitLabRequest> getLabRequestsForVisit(UUID recordId) {
        return visitLabRequestRepository.findByMedicalRecordIdOrderByCreatedAtAsc(recordId);
    }

    /**
     * Add a lab test request.  Always allowed.
     * Auto-creates an ISSUED invoice line item immediately.
     */
    @Transactional
    public VisitLabRequest addLabRequest(UUID recordId,
                                          UUID priceItemId,
                                          String urgency,
                                          String clinicalNotes,
                                          String specialInstructions,
                                          UUID createdByStaffId) {

        enforceNotDischarged(recordId);
        MedicalRecord record = requireRecord(recordId);
        ServicePriceItem item = requirePriceItem(priceItemId);

        if (!"LABORATORY".equalsIgnoreCase(item.getType())) {
            throw new IllegalArgumentException(
                    "Item '" + item.getProductName() + "' is not a LABORATORY catalogue item.");
        }

        LabUrgency urgencyEnum = parseUrgency(urgency);

        VisitLabRequest lr = VisitLabRequest.builder()
                .medicalRecord(record)
                .priceItem(item)
                .testName(item.getProductName())
                .urgency(urgencyEnum)
                .clinicalNotes(clinicalNotes)
                .specialInstructions(specialInstructions)
                .status(VisitLabRequestStatus.PENDING)
                .createdById(createdByStaffId)
                .build();

        lr = visitLabRequestRepository.save(lr);
        log.info("Added lab request [{}] to visit [{}]", item.getProductName(), recordId);

        // Auto-create invoice line item
        addInvoiceLineItem(record, item.getProductName(), LineItemCategory.LAB, item.getPrice());

        return lr;
    }

    /**
     * Edit a lab request.  Rejected if the visit is locked.
     */
    @Transactional
    public VisitLabRequest updateLabRequest(UUID id,
                                             String urgency,
                                             String clinicalNotes,
                                             String specialInstructions) {

        VisitLabRequest lr = visitLabRequestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Lab request not found: " + id));

        enforceNotDischarged(lr.getMedicalRecord().getId());
        enforceEditLock(lr.getMedicalRecord().getId());

        if (urgency != null && !urgency.isBlank()) {
            lr.setUrgency(parseUrgency(urgency));
        }
        lr.setClinicalNotes(clinicalNotes);
        lr.setSpecialInstructions(specialInstructions);
        return visitLabRequestRepository.save(lr);
    }

    /**
     * Delete a lab request.  Rejected if the visit is locked.
     */
    @Transactional
    public void deleteLabRequest(UUID id) {
        VisitLabRequest lr = visitLabRequestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Lab request not found: " + id));
        enforceNotDischarged(lr.getMedicalRecord().getId());
        enforceEditLock(lr.getMedicalRecord().getId());
        visitLabRequestRepository.delete(lr);
    }

    // -----------------------------------------------------------------------
    // Confirm / send-to-payment
    // -----------------------------------------------------------------------

    /**
     * Confirms that all invoices for a visit+category are in ISSUED status so
     * the receptionist can see them.  Since {@code addInvoiceLineItem} already
     * sets every invoice to ISSUED on creation, this mainly acts as a guard
     * (ensures at least one item exists and promotes any stale DRAFT invoices).
     *
     * @return map with invoiceNumbers, total, count
     * @throws IllegalStateException if no items have been added yet
     */
    @Transactional
    public Map<String, Object> confirmSentToPayment(UUID visitId, LineItemCategory category) {
        // Check items exist
        if (category == LineItemCategory.LAB) {
            if (visitLabRequestRepository.findByMedicalRecordIdOrderByCreatedAtAsc(visitId).isEmpty()) {
                throw new IllegalStateException("No lab tests have been added to this visit yet.");
            }
        } else {
            if (visitPrescriptionRepository.findByMedicalRecordIdOrderByCreatedAtAsc(visitId).isEmpty()) {
                throw new IllegalStateException("No prescriptions have been added to this visit yet.");
            }
        }

        // Find all non-voided invoices for this visit+category
        List<Invoice> invoices = invoiceRepository.findByMedicalRecordId(visitId).stream()
                .filter(inv -> inv.getStatus() != InvoiceStatus.VOIDED)
                .filter(inv -> inv.getLineItems().stream().anyMatch(li -> li.getCategory() == category))
                .toList();

        if (invoices.isEmpty()) {
            throw new IllegalStateException("No invoices found for this visit. Please re-add your items.");
        }

        // Promote any DRAFT invoices to ISSUED (safety net)
        for (Invoice inv : invoices) {
            if (inv.getStatus() == InvoiceStatus.DRAFT) {
                inv.setStatus(InvoiceStatus.ISSUED);
                invoiceRepository.save(inv);
            }
        }

        String numbers = invoices.stream()
                .map(Invoice::getInvoiceNumber)
                .collect(Collectors.joining(", "));
        BigDecimal total = invoices.stream()
                .map(Invoice::getTotalAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of("invoiceNumbers", numbers, "total", total, "count", invoices.size());
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void enforceNotDischarged(UUID medicalRecordId) {
        if (isVisitDischarged(medicalRecordId)) {
            throw new IllegalStateException(
                    "DISCHARGED: This patient has been discharged. " +
                    "No prescriptions or lab requests can be added, edited, or deleted.");
        }
    }

    private void enforceEditLock(UUID medicalRecordId) {
        if (isVisitLocked(medicalRecordId)) {
            throw new IllegalStateException(
                    "LOCKED: This visit has been paid. Editing and deleting existing items is disabled. " +
                    "You can still add new items.");
        }
    }

    /**
     * Find or create a single DRAFT invoice for the visit+category, then
     * append a new line item and mark the invoice as ISSUED.
     */
    private void addInvoiceLineItem(MedicalRecord record,
                                     String description,
                                     LineItemCategory category,
                                     BigDecimal price) {
        BigDecimal unitPrice = (price != null) ? price : BigDecimal.ZERO;

        // Try to reuse an existing DRAFT invoice for this visit+category
        Invoice invoice = invoiceRepository.findByMedicalRecordId(record.getId()).stream()
                .filter(inv -> inv.getStatus() == InvoiceStatus.DRAFT)
                .filter(inv -> inv.getLineItems().stream()
                        .anyMatch(li -> li.getCategory() == category))
                .findFirst()
                .orElseGet(() -> {
                    // Create a fresh invoice for this visit
                    String prefix = (category == LineItemCategory.PHARMACY) ? "RX" : "LAB";
                    Invoice newInv = Invoice.builder()
                            .patient(record.getPatient())
                            .medicalRecord(record)
                            .invoiceNumber(prefix + "-" + System.currentTimeMillis())
                            .invoiceDate(LocalDateTime.now())
                            .status(InvoiceStatus.DRAFT)
                            .subtotal(BigDecimal.ZERO)
                            .totalAmount(BigDecimal.ZERO)
                            .build();
                    return invoiceRepository.save(newInv);
                });

        InvoiceLineItem li = InvoiceLineItem.builder()
                .invoice(invoice)
                .description(description)
                .category(category)
                .quantity(1)
                .unitPrice(unitPrice)
                .lineTotal(unitPrice)
                .build();

        invoice.getLineItems().add(li);

        // Recalculate totals
        BigDecimal newTotal = invoice.getLineItems().stream()
                .map(InvoiceLineItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.setSubtotal(newTotal);
        invoice.setTotalAmount(newTotal);
        invoice.setStatus(InvoiceStatus.ISSUED);   // Now visible to receptionist

        invoiceRepository.save(invoice);
    }

    private MedicalRecord requireRecord(UUID id) {
        return medicalRecordRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Visit (MedicalRecord) not found: " + id));
    }

    private ServicePriceItem requirePriceItem(UUID id) {
        return priceItemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Price catalogue item not found: " + id));
    }

    private static LabUrgency parseUrgency(String value) {
        if (value == null || value.isBlank()) return LabUrgency.ROUTINE;
        try {
            return LabUrgency.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LabUrgency.ROUTINE;
        }
    }
}
