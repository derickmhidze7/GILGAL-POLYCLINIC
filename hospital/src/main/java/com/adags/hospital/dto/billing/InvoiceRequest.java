package com.adags.hospital.dto.billing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceRequest(
        @NotNull(message = "Patient ID is required")
        UUID patientId,

        UUID medicalRecordId,

        LocalDate dueDate,

        String notes
) {}

