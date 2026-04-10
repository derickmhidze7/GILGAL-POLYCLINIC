package com.adags.hospital.dto.billing;

import java.math.BigDecimal;

/**
 * One row in the month-by-month revenue table on the admin Revenue page.
 */
public record MonthlyRevenueDto(
        int    monthNum,
        String monthName,
        BigDecimal total) {
}
