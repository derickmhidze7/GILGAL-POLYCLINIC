package com.adags.hospital.domain.ward;

import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.pricing.ServicePriceItem;
import com.adags.hospital.domain.staff.Staff;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ward_lab_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WardLabRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_assignment_id", nullable = false)
    private WardPatientAssignment wardAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_item_id")
    private ServicePriceItem priceItem;

    @Column(name = "test_name", nullable = false, length = 255)
    private String testName;

    @Column(name = "urgency", length = 30)
    @Builder.Default
    private String urgency = "ROUTINE";

    @Column(name = "clinical_notes", columnDefinition = "TEXT")
    private String clinicalNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private Staff requestedBy;

    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    private LocalDateTime requestedAt = LocalDateTime.now();
}
