package com.adags.hospital.domain.billing;

public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PARTIALLY_PAID,
    PAID,
    CANCELLATION_PENDING,
    VOIDED,
    TERMINATED
}
