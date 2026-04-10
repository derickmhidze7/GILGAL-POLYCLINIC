package com.adags.hospital.dto.lab;

import com.adags.hospital.domain.lab.LabInterpretation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LabResultDto(
        @NotNull(message = "Lab request ID is required")
        UUID labRequestId,

        @NotNull(message = "Performed by (staff) ID is required")
        UUID performedById,

        @NotBlank(message = "Result value is required")
        String resultValue,

        String referenceRange,
        String unit,

        @NotNull(message = "Interpretation is required")
        LabInterpretation interpretation,

        String notes
) {}
