package com.adags.hospital.domain.pharmacy;

import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.medicalrecord.Prescription;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.visit.VisitPrescription;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "dispensed_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DispensedItem extends BaseEntity {

    /** Legacy prescription (nullable — null when dispensing a V26 VisitPrescription). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescription_id", nullable = true)
    private Prescription prescription;

    /** V26 visit-prescription reference (nullable — null for legacy dispenses). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_prescription_id", nullable = true)
    private VisitPrescription visitPrescription;

    /**
     * Legacy dispense path (medication-based prescriptions).
     * Nullable so that new price-item/StockBatch-based dispenses can leave it null.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", nullable = true)
    private InventoryItem inventoryItem;

    /**
     * New dispense path (price-item + StockBatch).
     * Set when dispensing via the stock-management system.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_batch_id", nullable = true)
    private StockBatch stockBatch;

    @Column(name = "quantity_dispensed", nullable = false)
    private Integer quantityDispensed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispensed_by_id", nullable = false)
    private Staff dispensedBy;

    @Column(name = "dispensed_at", nullable = false)
    @Builder.Default
    private LocalDateTime dispensedAt = LocalDateTime.now();

    @Column(name = "dispensing_notes", length = 500)
    private String dispensingNotes;
}
