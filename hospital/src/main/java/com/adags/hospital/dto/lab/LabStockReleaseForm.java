package com.adags.hospital.dto.lab;

import jakarta.validation.constraints.Size;

public record LabStockReleaseForm(
        Integer releasedQuantity,
        @Size(max = 500) String responseNotes
) {}
