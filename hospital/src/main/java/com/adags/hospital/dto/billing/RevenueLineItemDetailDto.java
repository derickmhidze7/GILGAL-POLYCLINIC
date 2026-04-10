package com.adags.hospital.dto.billing;

import com.adags.hospital.domain.billing.LineItemCategory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row in the Revenue drill-down / items detail view.
 * Represents a single {@link com.adags.hospital.domain.billing.InvoiceLineItem}
 * augmented with patient and staff information.
 */
public record RevenueLineItemDetailDto(
        String           invoiceNumber,
        LocalDateTime    invoiceDate,
        String           invoiceStatus,
        String           itemDescription,
        LineItemCategory category,
        int              quantity,
        BigDecimal       unitPrice,
        BigDecimal       lineTotal,
        String           patientName,
        /** Full name of the staff member who raised the invoice (may be "—"). */
        String           staffName,
        /** Role label such as "DOCTOR", "LAB TECHNICIAN", "PHARMACIST" (may be "—"). */
        String           staffRole) {
}
