package com.adags.hospital.dto.medicalrecord;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record MedicalRecordResponse(
        UUID id,
        UUID patientId,
        String patientFullName,
        UUID attendingDoctorId,
        String attendingDoctorFullName,
        UUID appointmentId,
        LocalDateTime visitDate,
        String clinicalNotes,
        LocalDate followUpDate,
        LocalDateTime createdAt
) {}
