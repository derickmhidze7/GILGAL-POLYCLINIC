package com.adags.hospital.domain.ward;

import com.adags.hospital.domain.appointment.Appointment;
import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.patient.Patient;
import com.adags.hospital.domain.staff.Staff;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ward_patient_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WardPatientAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_nurse_id")
    private Staff assignedNurse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_doctor_id")
    private Staff assignedByDoctor;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private WardPatientStatus status = WardPatientStatus.ADMITTED;

    @Column(name = "ward", length = 100)
    private String ward;

    @Column(name = "bed_number", length = 20)
    private String bedNumber;

    /** Per-day rate for this ward in TZSh (set when ward is assigned). */
    @Column(name = "ward_daily_rate", precision = 12, scale = 2)
    private BigDecimal wardDailyRate;

    /** UUID of the ward invoice (WRD-…) created when the ward was assigned. */
    @Column(name = "ward_invoice_id")
    private UUID wardInvoiceId;

    @Column(name = "admit_date", nullable = false)
    @Builder.Default
    private LocalDateTime admitDate = LocalDateTime.now();

    @Column(name = "discharge_date")
    private LocalDateTime dischargeDate;

    @Column(name = "admission_notes", columnDefinition = "TEXT")
    private String admissionNotes;

    @OneToMany(mappedBy = "wardAssignment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<VitalSigns> vitalSigns = new ArrayList<>();

    @OneToMany(mappedBy = "wardAssignment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MedicationAdministrationRecord> medicationRecords = new ArrayList<>();

    @OneToMany(mappedBy = "wardAssignment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<WoundCareNote> woundCareNotes = new ArrayList<>();

    @OneToMany(mappedBy = "wardAssignment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<WardPrescription> wardPrescriptions = new ArrayList<>();
}
