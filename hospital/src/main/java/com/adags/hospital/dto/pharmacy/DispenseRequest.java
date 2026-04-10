package com.adags.hospital.dto.pharmacy;

import lombok.Data;

import java.util.UUID;

@Data
public class DispenseRequest {
    private UUID prescriptionId;
    /** Old InventoryItem-based path (used for legacy/medication prescriptions). */
    private UUID inventoryItemId;
    /** New StockBatch-based path (used for price-item prescriptions). */
    private UUID stockBatchId;
    private Integer quantityDispensed;
    private String dispensingNotes;
    private String counsellingNotes;
}
