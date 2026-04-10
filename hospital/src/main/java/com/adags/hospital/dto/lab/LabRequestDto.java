package com.adags.hospital.dto.lab;

import com.adags.hospital.domain.lab.LabUrgency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LabRequestDto(
        @NotNull(message = "Patient ID is required")
        UUID patientId,

        @NotNull(message = "Requesting doctor ID is required")
        UUID requestingDoctorId,

        UUID medicalRecordId,

        @NotBlank(message = "Test name is required")
        String testName,

        String testCode,

        LabUrgency urgency
) {}
