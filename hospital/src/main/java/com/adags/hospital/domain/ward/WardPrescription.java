package com.adags.hospital.domain.ward;

import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.pricing.ServicePriceItem;
import com.adags.hospital.domain.staff.Staff;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ward_prescriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WardPrescription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_assignment_id", nullable = false)
    private WardPatientAssignment wardAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_item_id")
    private ServicePriceItem priceItem;

    @Column(name = "medication_name", nullable = false, length = 255)
    private String medicationName;

    @Column(name = "route", length = 50)
    private String route;

    @Column(name = "instructions", length = 500)
    private String instructions;

    @Column(name = "doses_per_day")
    private Integer dosesPerDay;

    @Column(name = "quantity_per_dose")
    private Integer quantityPerDose;

    @Column(name = "number_of_days")
    private Integer numberOfDays;

    @Column(name = "dispense_as_whole", nullable = false)
    @Builder.Default
    private boolean dispenseAsWhole = false;

    @Column(name = "total_quantity")
    private Integer totalQuantity;

    @Column(name = "dispensed_quantity", nullable = false)
    @Builder.Default
    private int dispensedQuantity = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescribed_by_id", nullable = false)
    private Staff prescribedBy;

    @Column(name = "prescribed_at", nullable = false)
    @Builder.Default
    private LocalDateTime prescribedAt = LocalDateTime.now();

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
