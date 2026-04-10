package com.adags.hospital.dto.consultation;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lab test / service catalogue search result for live autocomplete.
 */
@Data
@Builder
public class LabTestSearchResponse {
    private UUID id;
    private String testName;
    private String classification;   // e.g. HAEMATOLOGY, BIOCHEMISTRY
    private String productCode;
    private BigDecimal price;
}
