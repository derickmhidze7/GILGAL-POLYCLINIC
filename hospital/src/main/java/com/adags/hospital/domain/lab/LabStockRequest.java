package com.adags.hospital.domain.lab;

import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.pharmacy.StockItem;
import com.adags.hospital.domain.staff.Staff;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "lab_stock_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabStockRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id")
    private Staff requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_item_id", nullable = false)
    private StockItem stockItem;

    @Column(name = "requested_quantity", nullable = false)
    private Integer requestedQuantity;

    @Column(name = "released_quantity")
    private Integer releasedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LabStockRequestStatus status = LabStockRequestStatus.PENDING;

    @Column(name = "request_notes", length = 500)
    private String requestNotes;

    @Column(name = "response_notes", length = 500)
    private String responseNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by_id")
    private Staff handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;
}
