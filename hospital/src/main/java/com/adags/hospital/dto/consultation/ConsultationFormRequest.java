package com.adags.hospital.dto.consultation;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;
import com.adags.hospital.dto.consultation.SurgeryOrderRequest;

/**
 * Full consultation form — submitted as JSON body via AJAX POST.
 */
@Data
public class ConsultationFormRequest {

    // ── History & Examination ────────────────────────────────────────────────
    private String chiefComplaints;
    private String historyOfPresentingIllness;
    private String pastMedicalHistory;
    private String pastSurgicalHistory;
    private String familySocialHistory;
    private String drugFoodAllergies;
    private String comorbidities;
    private String currentSymptoms;
    private String modeOfAmbulation;
    private Boolean hasPain;
    private Integer painScore;
    private String painLocation;
    private Boolean fallRisk;
    private Integer fallScore;
    private Boolean infectiousDiseaseRisk;
    private String physicalExamination;
    private String clinicalNotes;

    // ── Diagnosis & Treatment ────────────────────────────────────────────────
    private String provisionalDiagnosis;
    private String finalDiagnosis;
    private String treatmentPlan;

    // ── Disposition ──────────────────────────────────────────────────────────
    private String nextStep;             // DISCHARGED | ADMITTED | REFERRED_DOCTOR | REFERRED_SPECIALIST
    private LocalDate followUpDate;
    private String followUpInstructions;
    private UUID forwardedToDoctorId;    // populated when nextStep is REFERRED_*

    // ── Ward Admission (populated when nextStep is ADMITTED) ─────────────────
    private String  wardName;              // doctor's suggested ward/unit
    private UUID    nurseId;               // pre-assigned ward nurse
    private Integer expectedAdmissionDays; // expected length of stay (days)

    // ── Surgery Order (populated when nextStep is SCHEDULE_SURGERY) ───────────
    private SurgeryOrderRequest surgeryOrder;

    // ── Action ───────────────────────────────────────────────────────────────
    private boolean finalize;            // true → mark FINALIZED, false → save as OPEN draft
}
