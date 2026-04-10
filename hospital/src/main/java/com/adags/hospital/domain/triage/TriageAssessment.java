package com.adags.hospital.domain.triage;

import com.adags.hospital.domain.appointment.Appointment;
import com.adags.hospital.domain.billing.Invoice;
import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.patient.Patient;
import com.adags.hospital.domain.staff.Staff;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "triage_assessments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TriageAssessment extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nurse_id", nullable = false)
    private Staff nurse;

    @Column(name = "assessment_date_time", nullable = false)
    @Builder.Default
    private LocalDateTime assessmentDateTime = LocalDateTime.now();

    @Column(name = "chief_complaint", nullable = true, length = 500)
    private String chiefComplaint;

    // ── Vital Signs ──────────────────────────────────────────────────────────

    @Column(name = "temperature", precision = 5, scale = 2)
    private BigDecimal temperature;

    @Column(name = "blood_pressure_systolic")
    private Integer bloodPressureSystolic;

    @Column(name = "blood_pressure_diastolic")
    private Integer bloodPressureDiastolic;

    @Column(name = "pulse_rate")
    private Integer pulseRate;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "oxygen_saturation", precision = 5, scale = 2)
    private BigDecimal oxygenSaturation;

    // ── Anthropometric ───────────────────────────────────────────────────────

    @Column(name = "weight", precision = 6, scale = 2)
    private BigDecimal weight;

    @Column(name = "height", precision = 5, scale = 2)
    private BigDecimal height;

    @Column(name = "bmi", precision = 5, scale = 2)
    private BigDecimal bmi;

    // ── Medical History ──────────────────────────────────────────────────────

    @Column(name = "known_allergies", columnDefinition = "TEXT")
    private String knownAllergies;

    @Column(name = "comorbidities", columnDefinition = "TEXT")
    private String comorbidities;

    @Column(name = "current_symptoms", columnDefinition = "TEXT")
    private String currentSymptoms;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_of_ambulation", length = 30)
    private ModeOfAmbulation modeOfAmbulation;

    // ── Pain Assessment ───────────────────────────────────────────────────────

    @Column(name = "has_pain", nullable = false)
    @Builder.Default
    private boolean hasPain = false;

    @Column(name = "pain_score")
    private Integer painScore;

    @Column(name = "pain_location", length = 255)
    private String painLocation;

    // ── Risk Assessment ───────────────────────────────────────────────────────

    @Column(name = "fall_risk", nullable = false)
    @Builder.Default
    private boolean fallRisk = false;

    @Column(name = "fall_score")
    private Integer fallScore;

    // ── Infectious Disease Risk ───────────────────────────────────────────────

    @Column(name = "infectious_disease_risk", nullable = false)
    @Builder.Default
    private boolean infectiousDiseaseRisk = false;

    // ── Triage Priority & Notes ───────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "triage_priority", nullable = false, length = 20)
    private TriagePriority triagePriority;

    @Column(name = "notes", length = 2000)
    private String notes;

    // ── Referral ──────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_doctor_id")
    private Staff referredDoctor;

    /** "DOCTOR" or "SPECIALIST_DOCTOR" — stored so display doesn't need FK lookup */
    @Column(name = "referral_type", length = 20)
    private String referralType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_invoice_id")
    private Invoice consultationInvoice;
}
