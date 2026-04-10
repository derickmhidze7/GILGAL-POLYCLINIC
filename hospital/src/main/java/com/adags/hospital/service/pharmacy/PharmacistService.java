package com.adags.hospital.service.pharmacy;

import com.adags.hospital.domain.medicalrecord.Prescription;
import com.adags.hospital.domain.medicalrecord.PrescriptionPharmacyStatus;
import com.adags.hospital.domain.pharmacy.DispensedItem;
import com.adags.hospital.domain.pharmacy.InventoryItem;
import com.adags.hospital.domain.pharmacy.StockBatch;
import com.adags.hospital.domain.pharmacy.StockItem;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.visit.VisitPrescription;
import com.adags.hospital.domain.visit.VisitPrescriptionStatus;
import com.adags.hospital.dto.pharmacy.DispenseRequest;
import com.adags.hospital.exception.BusinessRuleException;
import com.adags.hospital.exception.ResourceNotFoundException;
import com.adags.hospital.repository.medicalrecord.PrescriptionRepository;
import com.adags.hospital.repository.pharmacy.DispensedItemRepository;
import com.adags.hospital.repository.pharmacy.InventoryRepository;
import com.adags.hospital.repository.pharmacy.StockBatchRepository;
import com.adags.hospital.repository.pharmacy.StockItemRepository;
import com.adags.hospital.repository.triage.TriageRepository;
import com.adags.hospital.repository.visit.VisitPrescriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PharmacistService {

    private final PrescriptionRepository  prescriptionRepository;
    private final InventoryRepository     inventoryRepository;
    private final DispensedItemRepository dispensedItemRepository;
    private final TriageRepository        triageRepository;
    private final StockBatchRepository    stockBatchRepository;
    private final StockItemRepository     stockItemRepository;
    private final StockService            stockService;
    private final VisitPrescriptionRepository visitPrescriptionRepository;

    // ----------------------------------------------------------------
    //  Dispense queue — all pending / in-progress prescriptions
    // ----------------------------------------------------------------
    public List<Prescription> getDispenseQueue() {
        return prescriptionRepository.findPendingPharmacyQueue();
    }

    // ----------------------------------------------------------------
    //  Allergy warnings — check medication name against patient allergies
    // ----------------------------------------------------------------
    public List<String> getAllergyWarnings(UUID prescriptionId) {
        Prescription prescription = prescriptionRepository.findByIdWithDetails(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", "id", prescriptionId));

        // Support both old Medication-based and new PriceItem-based prescriptions
        String medicationName;
        String brandName = "";
        if (prescription.getMedication() != null) {
            medicationName = prescription.getMedication().getGenericName().toLowerCase();
            if (prescription.getMedication().getBrandName() != null) {
                brandName = prescription.getMedication().getBrandName().toLowerCase();
            }
        } else if (prescription.getPriceItem() != null) {
            medicationName = prescription.getPriceItem().getProductName().toLowerCase();
        } else {
            return Collections.emptyList();
        }

        List<String> warnings = new ArrayList<>();

        // 1 — check Patient.allergies (ElementCollection)
        List<String> patientAllergies = prescription.getMedicalRecord().getPatient().getAllergies();
        if (patientAllergies != null) {
            for (String allergy : patientAllergies) {
                String a = allergy.toLowerCase();
                if (medicationName.contains(a) || brandName.contains(a)
                        || a.contains(medicationName)) {
                    warnings.add("Patient allergy record: " + allergy);
                }
            }
        }

        // 2 — check TriageAssessment.knownAllergies (free text) via current appointment
        var appointment = prescription.getMedicalRecord().getAppointment();
        if (appointment != null) {
            final String capMedName = medicationName;
            final String capBrandName = brandName;
            triageRepository.findByAppointmentId(appointment.getId()).ifPresent(triage -> {
                String knownAllergies = triage.getKnownAllergies();
                if (knownAllergies != null && !knownAllergies.isBlank()) {
                    String lower = knownAllergies.toLowerCase();
                    if (lower.contains(capMedName) || (!capBrandName.isEmpty() && lower.contains(capBrandName))) {
                        warnings.add("Triage record allergies: " + knownAllergies);
                    }
                }
            });
        }

        return warnings;
    }

    // ----------------------------------------------------------------
    //  Get inventory options for a specific prescription's medication
    // ----------------------------------------------------------------
    public List<InventoryItem> getInventoryForPrescription(UUID prescriptionId) {
        Prescription prescription = prescriptionRepository.findByIdWithDetails(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", "id", prescriptionId));
        // If prescription uses the new price-item system, skip old inventory
        if (prescription.getMedication() == null) return Collections.emptyList();
        return inventoryRepository.findByMedicationId(
                prescription.getMedication().getId(),
                org.springframework.data.domain.Pageable.unpaged()).getContent();
    }

    /**
     * Returns available {@link StockBatch} entries (FEFO-ordered) for a
     * prescription that uses the new price-item system.
     * Returns an empty list for legacy medication-based prescriptions.
     */
    public List<StockBatch> getStockBatchesForPrescription(UUID prescriptionId) {
        Prescription prescription = prescriptionRepository.findByIdWithDetails(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", "id", prescriptionId));
        if (prescription.getPriceItem() == null) return Collections.emptyList();
        return stockBatchRepository.findAvailableByPriceItemId(prescription.getPriceItem().getId());
    }

    // ----------------------------------------------------------------
    //  Dispense item — save dispensed_item, reduce stock, update status
    // ----------------------------------------------------------------
    @Transactional
    public DispensedItem dispense(DispenseRequest req, Staff pharmacist) {

        Prescription prescription = prescriptionRepository.findByIdWithDetails(req.getPrescriptionId())
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", "id", req.getPrescriptionId()));

        if (prescription.isDispensed()) {
            throw new BusinessRuleException("Prescription is already fully dispensed.");
        }

        int qty = req.getQuantityDispensed() != null ? req.getQuantityDispensed() : 1;

        DispensedItem dispensed;
        if (req.getStockBatchId() != null) {
            // ── New path: price-item prescription + stock batch ──────────────
            stockService.decreaseStockBatch(req.getStockBatchId(), qty);

            dispensed = DispensedItem.builder()
                    .prescription(prescription)
                    .stockBatch(stockBatchRepository.getReferenceById(req.getStockBatchId()))
                    .quantityDispensed(qty)
                    .dispensedBy(pharmacist)
                    .dispensingNotes(req.getDispensingNotes())
                    .build();
        } else {
            // ── Old path: medication + InventoryItem ─────────────────────────
            InventoryItem item = inventoryRepository.findById(req.getInventoryItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", "id", req.getInventoryItemId()));

            if (item.getQuantityInStock() < qty) {
                throw new BusinessRuleException("Insufficient stock. Available: " + item.getQuantityInStock());
            }
            item.setQuantityInStock(item.getQuantityInStock() - qty);
            inventoryRepository.save(item);

            dispensed = DispensedItem.builder()
                    .prescription(prescription)
                    .inventoryItem(item)
                    .quantityDispensed(qty)
                    .dispensedBy(pharmacist)
                    .dispensingNotes(req.getDispensingNotes())
                    .build();
        }
        dispensedItemRepository.save(dispensed);

        // Update prescription status
        prescription.setDispensed(true);
        prescription.setPharmacyStatus(PrescriptionPharmacyStatus.DISPENSED);
        if (req.getCounsellingNotes() != null && !req.getCounsellingNotes().isBlank()) {
            prescription.setCounsellingNotes(req.getCounsellingNotes());
        }
        prescriptionRepository.save(prescription);

        return dispensed;
    }

    // ----------------------------------------------------------------
    //  History — items dispensed by this pharmacist
    // ----------------------------------------------------------------
    public List<DispensedItem> getMyHistory(UUID staffId) {
        return dispensedItemRepository.findByDispensedByIdWithDetails(staffId);
    }

    // ----------------------------------------------------------------
    //  Get a single prescription (for the dispense form)
    // ----------------------------------------------------------------
    public Prescription getPrescriptionById(UUID id) {
        return prescriptionRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", "id", id));
    }

    // ================================================================
    //  V26 VisitPrescription — payment-gated dispense
    // ================================================================

    /** Ready-to-dispense queue: PENDING_DISPENSING + pharmacy invoice PAID. */
    public List<VisitPrescription> getVisitDispenseQueue() {
        return visitPrescriptionRepository.findPaidPendingDispenseQueue();
    }

    /** Prescriptions waiting for payment at reception (not yet PAID). */
    public List<VisitPrescription> getVisitAwaitingPayment() {
        return visitPrescriptionRepository.findAwaitingPaymentQueue();
    }

    /** Load a single V26 prescription with patient + priceItem eagerly fetched. */
    public VisitPrescription getVisitPrescriptionById(UUID id) {
        return visitPrescriptionRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("VisitPrescription", "id", id));
    }

    /**
     * Available FEFO stock batches for the medication referenced by a V26 prescription.
     * If the StockItem was created via legacy seeding (no batches but currentQuantity > 0),
     * a migration batch is auto-created from the existing quantity.
     */
    @Transactional
    public List<StockBatch> getStockBatchesForVisitPrescription(UUID id) {
        VisitPrescription vp = visitPrescriptionRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("VisitPrescription", "id", id));
        if (vp.getPriceItem() == null) return Collections.emptyList();

        UUID priceItemId = vp.getPriceItem().getId();
        List<StockBatch> batches = stockBatchRepository.findAvailableByPriceItemId(priceItemId);

        // Auto-heal: if stock_items has quantity but zero batch rows (legacy seeded data),
        // create a migration batch so the pharmacist can dispense immediately.
        if (batches.isEmpty()) {
            var stockItemOpt = stockItemRepository.findByPriceItemId(priceItemId);
            if (stockItemOpt.isPresent() && stockItemOpt.get().getCurrentQuantity() > 0) {
                StockItem stockItem = stockItemOpt.get();
                String productName = vp.getPriceItem().getProductName();
                String letters = productName.replaceAll("[^A-Za-z]", "").toUpperCase();
                String prefix = letters.length() >= 3 ? letters.substring(0, 3) : (letters.isEmpty() ? "UNK" : letters);
                String batchNum = "BATCH-" + prefix + "-"
                        + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd").format(java.time.LocalDate.now())
                        + "-000";
                StockBatch migrationBatch = StockBatch.builder()
                        .stockItem(stockItem)
                        .batchNumber(batchNum)
                        .quantityReceived(stockItem.getCurrentQuantity())
                        .remainingQuantity(stockItem.getCurrentQuantity())
                        .expiryDate(java.time.LocalDate.now().plusYears(2))
                        .notes("Auto-migrated from legacy stock")
                        .receivedAt(java.time.LocalDateTime.now())
                        .build();
                stockBatchRepository.save(migrationBatch);
                batches = stockBatchRepository.findAvailableByPriceItemId(priceItemId);
            }
        }
        return batches;
    }

    /** Dispense a V26 VisitPrescription from a specific StockBatch. */
    @Transactional
    public DispensedItem dispenseVisit(UUID visitPrescriptionId, DispenseRequest req, Staff pharmacist) {
        VisitPrescription vp = visitPrescriptionRepository.findByIdWithDetails(visitPrescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("VisitPrescription", "id", visitPrescriptionId));

        if (vp.getStatus() == VisitPrescriptionStatus.DISPENSED) {
            throw new BusinessRuleException("This prescription has already been dispensed.");
        }
        if (req.getStockBatchId() == null) {
            throw new BusinessRuleException("Please select a stock batch to dispense from.");
        }

        int qty = req.getQuantityDispensed() != null ? req.getQuantityDispensed() : 1;
        stockService.decreaseStockBatch(req.getStockBatchId(), qty);

        DispensedItem dispensed = DispensedItem.builder()
                .visitPrescription(vp)
                .stockBatch(stockBatchRepository.getReferenceById(req.getStockBatchId()))
                .quantityDispensed(qty)
                .dispensedBy(pharmacist)
                .dispensingNotes(req.getDispensingNotes())
                .build();
        dispensedItemRepository.save(dispensed);

        vp.setStatus(VisitPrescriptionStatus.DISPENSED);
        vp.setDispensedQty(qty);
        vp.setDispensedAt(java.time.LocalDateTime.now());
        vp.setDispensedById(pharmacist.getId());
        if (req.getCounsellingNotes() != null && !req.getCounsellingNotes().isBlank()) {
            vp.setCounsellingNotes(req.getCounsellingNotes());
        }
        visitPrescriptionRepository.save(vp);
        return dispensed;
    }
}
