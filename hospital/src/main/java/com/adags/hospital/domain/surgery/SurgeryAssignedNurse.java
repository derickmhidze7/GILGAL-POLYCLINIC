package com.adags.hospital.domain.surgery;

import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.staff.Staff;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "surgery_assigned_nurses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurgeryAssignedNurse extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "surgery_order_id", nullable = false)
    private SurgeryOrder surgeryOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nurse_id", nullable = false)
    private Staff nurse;

    @Column(name = "nurse_role", length = 60, nullable = false)
    @Builder.Default
    private String nurseRole = "SCRUB_NURSE";
}
