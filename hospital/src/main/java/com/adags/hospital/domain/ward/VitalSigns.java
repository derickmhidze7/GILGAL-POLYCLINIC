package com.adags.hospital.domain.ward;

import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.patient.Patient;
import com.adags.hospital.domain.staff.Staff;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vital_signs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VitalSigns extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_assignment_id", nullable = false)
    private WardPatientAssignment wardAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_id")
    private Staff recordedBy;

    @Column(name = "recorded_at", nullable = false)
    @Builder.Default
    private LocalDateTime recordedAt = LocalDateTime.now();

    @Column(name = "bp_systolic")
    private Integer bpSystolic;

    @Column(name = "bp_diastolic")
    private Integer bpDiastolic;

    @Column(name = "pulse_rate")
    private Integer pulseRate;

    @Column(name = "temperature", precision = 4, scale = 1)
    private BigDecimal temperature;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "spo2")
    private Integer spo2;

    @Column(name = "pain_score")
    private Integer painScore;

    @Column(name = "has_alerts", nullable = false)
    @Builder.Default
    private Boolean hasAlerts = false;

    @Column(name = "alert_details", columnDefinition = "TEXT")
    private String alertDetails;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
