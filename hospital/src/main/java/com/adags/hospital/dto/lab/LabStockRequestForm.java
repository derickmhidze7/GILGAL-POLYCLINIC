package com.adags.hospital.dto.lab;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record LabStockRequestForm(
        @NotNull UUID stockItemId,
        @NotNull @Min(1) Integer requestedQuantity,
        @Size(max = 500) String requestNotes
) {}
