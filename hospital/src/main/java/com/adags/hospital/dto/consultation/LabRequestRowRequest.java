package com.adags.hospital.dto.consultation;

import lombok.Data;

import java.util.UUID;

/**
 * A single lab request row submitted from the consultation form.
 */
@Data
public class LabRequestRowRequest {
    private UUID servicePriceItemId;
    private String testName;        // display name from catalogue
    private String urgency;         // ROUTINE | URGENT
    private String specialInstructions;
}
