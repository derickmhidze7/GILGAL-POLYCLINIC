package com.adags.hospital.dto.lab;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Request DTO for saving / submitting a visit lab result.
 * Submitted as JSON from the visit-result-entry form via fetch().
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VisitLabResultRequest {

    // ── Sample information ──────────────────────────────────────────────────
    private String sampleType;
    private String sampleCollectedAt;   // ISO datetime string from datetime-local input
    private String sampleQuality;       // ADEQUATE, HAEMOLYSED, etc.
    private String sampleQualityNote;

    // ── Method & reagents ───────────────────────────────────────────────────
    private String methodology;
    private String reagentsUsed;

    // ── Findings & interpretation ───────────────────────────────────────────
    private String findings;
    private String interpretationText;
    private String referenceRangeNote;
    private String conclusion;

    // ── Parameters table ────────────────────────────────────────────────────
    private List<ParameterEntry> parameters = new ArrayList<>();

    @Data
    public static class ParameterEntry {
        private String parameterName;
        private String resultValue;
        private String unit;
        private String referenceRange;
        private String flag;      // H, L, N, C
        private String method;
        private int    sortOrder;
    }
}
