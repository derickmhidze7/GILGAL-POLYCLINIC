package com.adags.hospital.domain.pharmacy;

import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.pricing.ServicePriceItem;
import com.adags.hospital.domain.staff.Staff;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks pharmacy/lab reagent stock levels for items sourced from the
 * ServicePriceItem catalogue (type = 'PHARMACY' or 'LAB').
 *
 * <p>One row per price-catalogue item. Restocking increases
 * {@code currentQuantity} and adds a new {@link StockBatch} entry.
 * Dispensing decreases the batch {@code remainingQuantity} and mirrors
 * that change back onto {@code currentQuantity}.
 * Alert thresholds are computed relative to {@code lastBatchQuantity}:</p>
 * <ul>
 *   <li>≤ 50% → WARNING</li>
 *   <li>≤ 25% → CRITICAL</li>
 *   <li>≤ 10% → DANGER</li>
 * </ul>
 */
@Entity
@Table(name = "stock_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_item_id", nullable = false, unique = true)
    private ServicePriceItem priceItem;

    /** Running on-hand quantity (sum of all active batch remainingQuantity). */
    @Column(name = "current_quantity", nullable = false)
    private Integer currentQuantity;

    /**
     * Quantity from the most recent restock.
     * Used as the 100% baseline for alert-threshold calculations.
     */
    @Column(name = "last_batch_quantity", nullable = false)
    @Builder.Default
    private Integer lastBatchQuantity = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by_staff_id")
    private Staff addedBy;

    @Column(name = "last_restocked_at")
    private LocalDateTime lastRestockedAt;

    @Column(name = "supplier", length = 200)
    private String supplier;

    @Column(name = "notes", length = 500)
    private String notes;

    /** All restock batches for this item — ordered in FEFO by expiryDate.
     *  SUBSELECT ensures all batches load in a single query when the collection
     *  is first accessed (safe with spring.jpa.open-in-view=false). */
    @OneToMany(mappedBy = "stockItem", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("expiryDate ASC")
    @Fetch(FetchMode.SUBSELECT)
    @Builder.Default
    private List<StockBatch> batches = new ArrayList<>();
}
