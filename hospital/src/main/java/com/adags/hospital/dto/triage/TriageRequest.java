package com.adags.hospital.dto.triage;

import com.adags.hospital.domain.triage.ModeOfAmbulation;
import com.adags.hospital.domain.triage.TriagePriority;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record TriageRequest(
        @NotNull(message = "Patient ID is required")
        UUID patientId,

        @NotNull(message = "Nurse ID is required")
        UUID nurseId,

        UUID appointmentId,

        @Size(max = 500)
        String chiefComplaint,

        // ── Vital Signs ────────────────────────────────────────────────────────
        BigDecimal temperature,
        Integer bloodPressureSystolic,
        Integer bloodPressureDiastolic,
        Integer pulseRate,
        Integer respiratoryRate,
        BigDecimal oxygenSaturation,

        // ── Anthropometric ─────────────────────────────────────────────────────
        BigDecimal weight,
        BigDecimal height,
        BigDecimal bmi,

        // ── Medical History ────────────────────────────────────────────────────
        String knownAllergies,
        String comorbidities,
        String currentSymptoms,
        ModeOfAmbulation modeOfAmbulation,

        // ── Pain Assessment ────────────────────────────────────────────────────
        boolean hasPain,
        @Min(0) @Max(10)
        Integer painScore,
        String painLocation,

        // ── Risk Assessment ────────────────────────────────────────────────────
        boolean fallRisk,
        Integer fallScore,

        // ── Infectious Disease Risk ────────────────────────────────────────────
        boolean infectiousDiseaseRisk,

        // ── Priority & Notes ──────────────────────────────────────────────────
        @NotNull(message = "Triage priority is required")
        TriagePriority triagePriority,

        String notes,

        // ── Referral ──────────────────────────────────────────────────────────
        UUID referredDoctorId,
        String referralType
) {}
