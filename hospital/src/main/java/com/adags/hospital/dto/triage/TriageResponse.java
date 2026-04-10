package com.adags.hospital.dto.triage;

import com.adags.hospital.domain.triage.ModeOfAmbulation;
import com.adags.hospital.domain.triage.TriagePriority;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TriageResponse(
        UUID id,
        UUID patientId,
        String patientFullName,
        UUID nurseId,
        String nurseFullName,
        UUID appointmentId,
        LocalDateTime assessmentDateTime,
        String chiefComplaint,
        // Vital signs
        BigDecimal temperature,
        Integer bloodPressureSystolic,
        Integer bloodPressureDiastolic,
        Integer pulseRate,
        Integer respiratoryRate,
        BigDecimal oxygenSaturation,
        // Anthropometric
        BigDecimal weight,
        BigDecimal height,
        BigDecimal bmi,
        // Medical history
        String knownAllergies,
        String comorbidities,
        String currentSymptoms,
        ModeOfAmbulation modeOfAmbulation,
        // Pain
        boolean hasPain,
        Integer painScore,
        String painLocation,
        // Risk
        boolean fallRisk,
        Integer fallScore,
        // Infectious
        boolean infectiousDiseaseRisk,
        // Priority
        TriagePriority triagePriority,
        String notes,
        // Referral
        UUID referredDoctorId,
        String referredDoctorName,
        String referralType,
        UUID consultationInvoiceId
) {}
