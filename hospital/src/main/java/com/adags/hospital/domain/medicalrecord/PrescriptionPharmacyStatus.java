package com.adags.hospital.domain.medicalrecord;

public enum PrescriptionPharmacyStatus {
    /** Saved by doctor — not yet sent to payment. Not visible to pharmacist. */
    PENDING,
    /** Invoice created and sent to receptionist — awaiting payment. Not visible to pharmacist. */
    AWAITING_PAYMENT,
    /** Invoice paid by patient — cleared for pharmacist to dispense. */
    READY_TO_DISPENSE,
    IN_PROGRESS,
    PARTIALLY_DISPENSED,
    DISPENSED,
    ON_HOLD,
    OUT_OF_STOCK
}
