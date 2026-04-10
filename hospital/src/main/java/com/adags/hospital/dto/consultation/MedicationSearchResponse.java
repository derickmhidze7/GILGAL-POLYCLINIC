package com.adags.hospital.dto.consultation;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Medication search result for live autocomplete.
 */
@Data
@Builder
public class MedicationSearchResponse {
    private UUID id;
    private String genericName;
    private String brandName;
    private String form;
    private String strength;
    private boolean inStock;
    private int stockQty;
    /** Unit price in TZS from the service price catalogue. */
    private BigDecimal price;
}
