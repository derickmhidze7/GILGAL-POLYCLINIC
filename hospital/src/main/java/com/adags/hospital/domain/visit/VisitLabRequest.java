package com.adags.hospital.domain.visit;

import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.lab.LabUrgency;
import com.adags.hospital.domain.medicalrecord.MedicalRecord;
import com.adags.hospital.domain.pricing.ServicePriceItem;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A lab test request raised by a doctor during a visit.
 * Source of truth: ServicePriceItem (type = 'LABORATORY').
 * Completely separate from the legacy 'lab_requests' table.
 */
@Entity
@Table(name = "visit_lab_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitLabRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medical_record_id", nullable = false)
    private MedicalRecord medicalRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_item_id", nullable = false)
    private ServicePriceItem priceItem;

    /** Denormalised test name — stable even if catalogue is later edited. */
    @Column(name = "test_name", nullable = false, length = 255)
    private String testName;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency", nullable = false, length = 30)
    @Builder.Default
    private LabUrgency urgency = LabUrgency.ROUTINE;

    @Column(name = "clinical_notes", columnDefinition = "TEXT")
    private String clinicalNotes;

    @Column(name = "special_instructions", columnDefinition = "TEXT")
    private String specialInstructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private VisitLabRequestStatus status = VisitLabRequestStatus.PENDING;

    /** Summary of results entered by the lab technician. */
    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    // ── Extended result fields (V27) ──────────────────────────────────────

    @Column(name = "sample_type", length = 100)
    private String sampleType;

    @Column(name = "sample_collected_at")
    private LocalDateTime sampleCollectedAt;

    @Column(name = "sample_quality", length = 30)
    private String sampleQuality;

    @Column(name = "sample_quality_note", columnDefinition = "TEXT")
    private String sampleQualityNote;

    @Column(name = "methodology", length = 255)
    private String methodology;

    @Column(name = "reagents_used", columnDefinition = "TEXT")
    private String reagentsUsed;

    @Column(name = "findings", columnDefinition = "TEXT")
    private String findings;

    @Column(name = "interpretation_text", columnDefinition = "TEXT")
    private String interpretationText;

    @Column(name = "reference_range_note", length = 255)
    private String referenceRangeNote;

    @Column(name = "conclusion", length = 255)
    private String conclusion;

    @OneToMany(mappedBy = "visitLabRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<VisitLabResultParameter> resultParameters = new ArrayList<>();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Staff UUID of the requesting doctor. */
    @Column(name = "created_by_id")
    private UUID createdById;

    /** Binary content of the machine-output PDF uploaded by the lab tech. */
    @Lob
    @Column(name = "machine_pdf_data", columnDefinition = "BYTEA")
    private byte[] machinePdfData;

    /** Original filename of the uploaded machine-output PDF. */
    @Column(name = "machine_pdf_name", length = 255)
    private String machinePdfName;
}
