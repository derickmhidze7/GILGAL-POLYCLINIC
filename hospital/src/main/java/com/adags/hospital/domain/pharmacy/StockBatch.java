package com.adags.hospital.domain.pharmacy;

import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.staff.Staff;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a single restocking batch for a {@link StockItem}.
 *
 * <p>Batches are sorted by {@code expiryDate} ascending so that the system
 * automatically applies FEFO (First-Expired, First-Out) dispensing order.
 * Each time a prescription is dispensed, {@code remainingQuantity} decreases;
 * when it reaches zero the batch is exhausted.</p>
 */
@Entity
@Table(name = "stock_batches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockBatch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_item_id", nullable = false)
    private StockItem stockItem;

    /**
     * Auto-generated batch identifier, e.g. {@code BATCH-PAN-20260302-001}.
     * Format: {@code BATCH-<first-3-chars-of-item>-yyyyMMdd-<seq>}
     */
    @Column(name = "batch_number", nullable = false, length = 80)
    private String batchNumber;

    /** Original quantity received in this restock. */
    @Column(name = "quantity_received", nullable = false)
    private Integer quantityReceived;

    /** Running quantity still available (decremented on each dispense). */
    @Column(name = "remaining_quantity", nullable = false)
    private Integer remainingQuantity;

    /**
     * Expiry date drives FEFO ordering.
     * The dispense form shows batches nearest to expiry first.
     */
    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "supplier", length = 200)
    private String supplier;

    @Column(name = "notes", length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by_staff_id")
    private Staff addedBy;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private LocalDateTime receivedAt = LocalDateTime.now();
}
