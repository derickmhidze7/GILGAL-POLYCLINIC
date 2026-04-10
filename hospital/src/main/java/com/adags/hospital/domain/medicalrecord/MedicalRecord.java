package com.adags.hospital.domain.medicalrecord;

import com.adags.hospital.domain.appointment.Appointment;
import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.lab.LabRequest;
import com.adags.hospital.domain.patient.Patient;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.triage.ModeOfAmbulation;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "medical_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attending_doctor_id", nullable = false)
    private Staff attendingDoctor;

    @Column(name = "visit_date", nullable = false)
    @Builder.Default
    private LocalDateTime visitDate = LocalDateTime.now();

    // ── History & Examination ────────────────────────────────────────────────

    @Column(name = "chief_complaints", columnDefinition = "TEXT")
    private String chiefComplaints;

    @Column(name = "history_of_presenting_illness", columnDefinition = "TEXT")
    private String historyOfPresentingIllness;

    @Column(name = "past_medical_history", columnDefinition = "TEXT")
    private String pastMedicalHistory;

    @Column(name = "past_surgical_history", columnDefinition = "TEXT")
    private String pastSurgicalHistory;

    @Column(name = "family_social_history", columnDefinition = "TEXT")
    private String familySocialHistory;

    @Column(name = "drug_food_allergies", columnDefinition = "TEXT")
    private String drugFoodAllergies;

    @Column(name = "comorbidities", columnDefinition = "TEXT")
    private String comorbidities;

    @Column(name = "current_symptoms", columnDefinition = "TEXT")
    private String currentSymptoms;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_of_ambulation", length = 50)
    private ModeOfAmbulation modeOfAmbulation;

    @Column(name = "has_pain")
    private Boolean hasPain;

    @Column(name = "pain_score")
    private Integer painScore;

    @Column(name = "pain_location", length = 255)
    private String painLocation;

    @Column(name = "fall_risk")
    private Boolean fallRisk;

    @Column(name = "fall_score")
    private Integer fallScore;

    @Column(name = "infectious_disease_risk")
    private Boolean infectiousDiseaseRisk;

    @Column(name = "physical_examination", columnDefinition = "TEXT")
    private String physicalExamination;

    @Column(name = "clinical_notes", columnDefinition = "TEXT")
    private String clinicalNotes;

    // ── Diagnosis & Treatment ────────────────────────────────────────────────

    @Column(name = "provisional_diagnosis", length = 500)
    private String provisionalDiagnosis;

    @Column(name = "final_diagnosis", length = 500)
    private String finalDiagnosis;

    @Column(name = "treatment_plan", columnDefinition = "TEXT")
    private String treatmentPlan;

    @OneToMany(mappedBy = "medicalRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Diagnosis> diagnoses = new ArrayList<>();

    // ── Prescriptions & Lab Requests ─────────────────────────────────────────

    @OneToMany(mappedBy = "medicalRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Prescription> prescriptions = new ArrayList<>();

    @OneToMany(mappedBy = "medicalRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LabRequest> labRequests = new ArrayList<>();

    // ── Disposition ──────────────────────────────────────────────────────────

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "follow_up_instructions", columnDefinition = "TEXT")
    private String followUpInstructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_step", length = 30)
    private DispositionType nextStep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forwarded_to_doctor_id")
    private Staff forwardedToDoctor;

    @Column(name = "forwarded_type", length = 20)
    private String forwardedType;

    // ── Status ───────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "consultation_status", nullable = false, length = 20)
    @Builder.Default
    private ConsultationStatus consultationStatus = ConsultationStatus.OPEN;

    @Column(name = "doctor_note", columnDefinition = "TEXT")
    private String doctorNote;
}
