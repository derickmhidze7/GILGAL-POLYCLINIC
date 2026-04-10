package com.adags.hospital.domain.surgery;

import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.staff.Staff;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "surgery_postop_care")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurgeryPostopCare extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "surgery_order_id", nullable = false)
    private SurgeryOrder surgeryOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nurse_id")
    private Staff nurse;

    @Column(name = "recorded_at", nullable = false)
    @Builder.Default
    private LocalDateTime recordedAt = LocalDateTime.now();

    @Column(name = "consciousness_level", length = 40)
    private String consciousnessLevel;

    @Column(name = "blood_pressure", length = 20)
    private String bloodPressure;

    @Column(name = "pulse_rate")
    private Integer pulseRate;

    @Column(name = "spo2", precision = 5, scale = 2)
    private BigDecimal spo2;

    @Column(name = "pain_score")
    private Integer painScore;

    @Column(name = "temperature", precision = 5, scale = 2)
    private BigDecimal temperature;

    @Column(name = "recovery_notes", columnDefinition = "TEXT")
    private String recoveryNotes;

    @Column(name = "next_step", length = 40)
    private String nextStep;
}
