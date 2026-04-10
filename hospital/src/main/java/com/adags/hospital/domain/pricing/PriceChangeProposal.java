package com.adags.hospital.domain.pricing;

import com.adags.hospital.domain.staff.Staff;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A price-change (or new-item) proposal submitted by a pharmacist.
 * Becomes live in service_price_items only after admin approval.
 *
 * priceItem == null  → proposal to add a completely new pharmacy item
 * priceItem != null  → proposal to update an existing item's details / price
 */
@Entity
@Table(name = "price_change_proposals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceChangeProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Null means this is a brand-new item proposal. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_item_id")
    private ServicePriceItem priceItem;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "product_code", length = 100)
    private String productCode;

    @Column(name = "item_id", length = 100)
    private String itemId;

    @Column(name = "classification", length = 200)
    private String classification;

    @Column(name = "category", length = 200)
    private String category;

    @Column(name = "proposed_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal proposedPrice;

    /** Snapshot of the price at time of proposal (null for new items). */
    @Column(name = "current_price", precision = 12, scale = 2)
    private BigDecimal currentPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PriceProposalStatus status = PriceProposalStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposed_by_id")
    private Staff proposedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private Staff reviewedBy;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    /** "PHARMACY" or "LABORATORY" — null treated as legacy PHARMACY. */
    @Column(name = "type", length = 50)
    private String type;
}
