package com.adags.hospital.dto.medicalrecord;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record MedicalRecordRequest(
        @NotNull(message = "Patient ID is required")
        UUID patientId,

        @NotNull(message = "Attending doctor ID is required")
        UUID attendingDoctorId,

        UUID appointmentId,

        String clinicalNotes,

        LocalDate followUpDate
) {}
