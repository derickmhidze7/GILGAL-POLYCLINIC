package com.adags.hospital.dto.appointment;

import com.adags.hospital.domain.appointment.AppointmentStatus;
import com.adags.hospital.domain.appointment.AppointmentType;

import java.time.LocalDateTime;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        UUID patientId,
        String patientFullName,
        UUID doctorId,
        String doctorFullName,
        LocalDateTime scheduledDateTime,
        AppointmentType appointmentType,
        AppointmentStatus status,
        String notes,
        LocalDateTime createdAt
) {}
