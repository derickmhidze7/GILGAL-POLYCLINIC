package com.adags.hospital.domain.lab;

import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.staff.Staff;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "lab_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabResult extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_request_id", nullable = false)
    private LabRequest labRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_id", nullable = false)
    private Staff performedBy;

    @Column(name = "result_value", length = 500)
    private String resultValue;

    @Column(name = "reference_range", length = 200)
    private String referenceRange;

    @Column(name = "unit", length = 50)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "interpretation", nullable = false, length = 15)
    private LabInterpretation interpretation;

    @Column(name = "result_date_time", nullable = false)
    @Builder.Default
    private LocalDateTime resultDateTime = LocalDateTime.now();

    @Column(name = "notes")
    private String notes;

    // --- Verification & submission ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_id")
    private Staff verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "is_submitted", nullable = false)
    @Builder.Default
    private boolean submitted = false;

    @Column(name = "is_locked", nullable = false)
    @Builder.Default
    private boolean locked = false;

    @OneToMany(mappedBy = "labResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.List<LabResultParameter> parameters = new java.util.ArrayList<>();

    /** Relative path to an optionally uploaded PDF report (under app.upload.path). */
    @Column(name = "report_pdf_path", length = 512)
    private String reportPdfPath;

    /** Relative path to the machine-output PDF uploaded alongside the parameter rows. */
    @Column(name = "machine_pdf_path", length = 512)
    private String machinePdfPath;
}
