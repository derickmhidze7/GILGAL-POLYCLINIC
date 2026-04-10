package com.adags.hospital.domain.visit;

import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.medicalrecord.MedicalRecord;
import com.adags.hospital.domain.pricing.ServicePriceItem;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A prescription item added by a doctor during a visit.
 * Source of truth: ServicePriceItem (type = 'PHARMACY').
 * Completely separate from the legacy 'prescriptions' table.
 */
@Entity
@Table(name = "visit_prescriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitPrescription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medical_record_id", nullable = false)
    private MedicalRecord medicalRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_item_id", nullable = false)
    private ServicePriceItem priceItem;

    /** Denormalised name — stable even if catalogue entry is later edited. */
    @Column(name = "medication_name", nullable = false, length = 255)
    private String medicationName;

    @Column(name = "dosage", length = 100)
    private String dosage;

    @Column(name = "frequency", length = 100)
    private String frequency;

    @Column(name = "duration", length = 100)
    private String duration;

    @Column(name = "route", length = 100)
    private String route;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private VisitPrescriptionStatus status = VisitPrescriptionStatus.PENDING_DISPENSING;

    /** Total quantity to dispense as calculated by the doctor. Pre-fills the pharmacist's dispense form. */
    @Column(name = "total_quantity_to_dispense")
    private Integer totalQuantityToDispense;

    /** Filled by the pharmacist with an integer quantity only. */
    @Column(name = "dispensed_qty")
    private Integer dispensedQty;

    @Column(name = "dispensed_at")
    private LocalDateTime dispensedAt;

    /** Counselling notes entered by pharmacist at dispense time. */
    @Column(name = "counselling_notes", columnDefinition = "TEXT")
    private String counsellingNotes;

    /** Staff ID of the pharmacist who dispensed. */
    @Column(name = "dispensed_by_id")
    private UUID dispensedById;

    /** Staff UUID of the prescribing doctor. */
    @Column(name = "created_by_id")
    private UUID createdById;
}
