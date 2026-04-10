package com.adags.hospital.dto.appointment;

import com.adags.hospital.domain.appointment.AppointmentStatus;
import com.adags.hospital.domain.appointment.AppointmentType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record AppointmentRequest(
        @NotNull(message = "Patient ID is required")
        UUID patientId,

        UUID doctorId,

        @NotNull(message = "Scheduled date/time is required")
        @Future(message = "Appointment must be in the future")
        LocalDateTime scheduledDateTime,

        @NotNull(message = "Appointment type is required")
        AppointmentType appointmentType,

        AppointmentStatus status,

        String notes
) {}
