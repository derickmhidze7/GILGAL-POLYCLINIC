package com.adags.hospital.domain.lab;

import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.medicalrecord.MedicalRecord;
import com.adags.hospital.domain.patient.Patient;
import com.adags.hospital.domain.pricing.ServicePriceItem;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.surgery.SurgeryOrder;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "lab_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medical_record_id")
    private MedicalRecord medicalRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requesting_doctor_id", nullable = false)
    private Staff requestingDoctor;

    @Column(name = "test_name", nullable = false, length = 200)
    private String testName;

    @Column(name = "test_code", length = 50)
    private String testCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency", nullable = false, length = 15)
    @Builder.Default
    private LabUrgency urgency = LabUrgency.ROUTINE;

    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LabRequestStatus status = LabRequestStatus.PENDING;

    @Column(name = "special_instructions", columnDefinition = "TEXT")
    private String specialInstructions;

    /** Link to the service catalogue so pharmacy/billing can track the test cost. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_price_item_id")
    private ServicePriceItem servicePriceItem;

    /** Set when this lab request originated from a surgery order (pre-op or post-op). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "surgery_order_id")
    private SurgeryOrder surgeryOrder;

    @OneToOne(mappedBy = "labRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private LabResult result;

    // --- Sample tracking ---
    @Column(name = "sample_type", length = 50)
    private String sampleType;

    @Column(name = "sample_collected_at")
    private LocalDateTime sampleCollectedAt;

    @Column(name = "sample_received_at")
    private LocalDateTime sampleReceivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sample_quality", nullable = false, length = 20)
    @Builder.Default
    private SampleQuality sampleQuality = SampleQuality.ADEQUATE;
}
