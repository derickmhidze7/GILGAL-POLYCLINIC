package com.adags.hospital.domain.medicalrecord;

import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.pharmacy.Medication;
import com.adags.hospital.domain.pricing.ServicePriceItem;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "prescriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prescription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medical_record_id", nullable = false)
    private MedicalRecord medicalRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id")
    private Medication medication;

    /** Direct link to service_price_items for TZS pricing. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_item_id")
    private ServicePriceItem priceItem;

    @Column(name = "dosage", nullable = false, length = 100)
    private String dosage;

    @Column(name = "frequency", nullable = false, length = 100)
    private String frequency;

    @Column(name = "duration", nullable = false, length = 100)
    private String duration;

    @Column(name = "instructions", length = 500)
    private String instructions;

    /** Route of administration: ORAL, IV, IM, TOPICAL, INHALATION, SUBLINGUAL, etc. */
    @Column(name = "route", length = 50)
    private String route;

    @Column(name = "dispensed", nullable = false)
    @Builder.Default
    private boolean dispensed = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "pharmacy_status", nullable = false, length = 30)
    @Builder.Default
    private PrescriptionPharmacyStatus pharmacyStatus = PrescriptionPharmacyStatus.PENDING;

    @Column(name = "counselling_notes", columnDefinition = "TEXT")
    private String counsellingNotes;

    /** Number of times the medication is taken per day (e.g. 2 = twice daily). */
    @Column(name = "doses_per_day")
    private Integer dosesPerDay;

    /** Number of pills/units taken per dose (e.g. 3 = three tablets at a time). */
    @Column(name = "quantity_per_dose")
    private Integer quantityPerDose;

    /** Number of days the medication should be taken. */
    @Column(name = "number_of_days")
    private Integer numberOfDays;

    /**
     * When true the dosage-schedule fields are ignored and the doctor
     * is prescribing a fixed whole-unit quantity (e.g. one bottle/vial/pack).
     */
    @Column(name = "dispense_as_whole", nullable = false)
    @Builder.Default
    private boolean dispenseAsWhole = false;

    /**
     * Pre-calculated total units to dispense.
     * Standard: dosesPerDay × quantityPerDose × numberOfDays.
     * Whole: equals dosesPerDay (re-used as the "qty" field in whole mode).
     */
    @Column(name = "total_quantity_to_dispense")
    private Integer totalQuantityToDispense;
}
