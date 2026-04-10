package com.adags.hospital.dto.billing;

import java.math.BigDecimal;

/**
 * One row in the revenue-by-category table on the admin Revenue page.
 * {@code categoryCode} is the {@link com.adags.hospital.domain.billing.LineItemCategory} enum name
 * (e.g. "LAB", "CONSULTATION") used to build drill-down URLs.
 */
public record RevenueBreakdownDto(
        String  categoryCode,
        String  categoryLabel,
        BigDecimal total,
        long    invoiceCount) {
}
