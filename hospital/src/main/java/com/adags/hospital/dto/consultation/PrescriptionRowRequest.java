package com.adags.hospital.dto.consultation;

import lombok.Data;

import java.util.UUID;

/**
 * A single prescription row submitted from the consultation form.
 */
@Data
public class PrescriptionRowRequest {
    private UUID medicationId;
    private String drugName;    // display name, stored for convenience
    private String dose;
    private String frequency;
    private String duration;
    private String route;
    private String instructions;
}
