package com.adags.hospital.domain.surgery;

import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.medicalrecord.MedicalRecord;
import com.adags.hospital.domain.patient.Patient;
import com.adags.hospital.domain.pricing.ServicePriceItem;
import com.adags.hospital.domain.staff.Staff;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "surgery_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurgeryOrder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requesting_doctor_id", nullable = false)
    private Staff requestingDoctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medical_record_id")
    private MedicalRecord medicalRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_price_item_id")
    private ServicePriceItem servicePriceItem;

    @Column(name = "procedure_name", nullable = false, length = 255)
    private String procedureName;

    @Column(name = "surgery_type", length = 60, nullable = false)
    @Builder.Default
    private String surgeryType = "GENERAL";

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency", nullable = false, length = 30)
    @Builder.Default
    private SurgeryUrgency urgency = SurgeryUrgency.ELECTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "anesthesia_type", length = 50)
    private AnesthesiaType anesthesiaType;

    @Column(name = "scheduled_date")
    private LocalDateTime scheduledDate;

    @Column(name = "operating_theater", length = 100)
    private String operatingTheater;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private SurgeryStatus status = SurgeryStatus.SCHEDULED;

    @Column(name = "consent_obtained", nullable = false)
    @Builder.Default
    private boolean consentObtained = false;

    @Column(name = "preop_notes", columnDefinition = "TEXT")
    private String preopNotes;

    @Column(name = "postop_notes", columnDefinition = "TEXT")
    private String postopNotes;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(name = "actual_duration_minutes")
    private Integer actualDurationMinutes;

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "consent_document_path", length = 500)
    private String consentDocumentPath;

    /** ID of the billing invoice created when this surgery was scheduled. */
    @Column(name = "surgery_invoice_id")
    private java.util.UUID surgeryInvoiceId;

    /** True once a nurse or doctor has explicitly sent the surgery items for payment collection. */
    @Column(name = "sent_for_payment", nullable = false)
    @Builder.Default
    private boolean sentForPayment = false;

    @Column(name = "sent_for_payment_at")
    private LocalDateTime sentForPaymentAt;

    // ── Relationships ──────────────────────────────────────────────────────

    @OneToMany(mappedBy = "surgeryOrder", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SurgeryAssignedNurse> assignedNurses = new ArrayList<>();

    @OneToMany(mappedBy = "surgeryOrder", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SurgeryItemList> itemLists = new ArrayList<>();

    @OneToOne(mappedBy = "surgeryOrder", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private SurgeryIntraoperative intraoperative;

    @OneToMany(mappedBy = "surgeryOrder", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SurgeryPostopCare> postopCareRecords = new ArrayList<>();
}
