package com.adags.hospital.domain.surgery;

import com.adags.hospital.domain.billing.Invoice;
import com.adags.hospital.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "surgery_item_lists")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurgeryItemList extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "surgery_order_id", nullable = false)
    private SurgeryOrder surgeryOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private SurgeryItemListType itemType;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private int quantity = 1;

    @Column(name = "price", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "dispensed", nullable = false)
    @Builder.Default
    private boolean dispensed = false;

    @Column(name = "pharmacy_notes", columnDefinition = "TEXT")
    private String pharmacyNotes;

    /** True when this item is a lab test request (as opposed to a pharmacy/drug item). */
    @Column(name = "is_lab_item", nullable = false)
    @Builder.Default
    private boolean isLabItem = false;

    // ── Dosage / prescription metadata (pharmacy items only) ────────────────

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

    /** Invoice that covers billing for this item (null for older records). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;
}
