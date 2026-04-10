package com.adags.hospital.domain.ward;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Fixed ward options with per-day pricing.
 * Serengeti  : TZSh 20,000/day
 * Manyara    : TZSh 20,000/day
 * Labour Ward: TZSh 20,000/day
 */
public enum WardOption {

    SERENGETI("Serengeti", new BigDecimal("20000.00")),
    MANYARA("Manyara", new BigDecimal("20000.00")),
    LABOUR_WARD("Labour Ward", new BigDecimal("20000.00"));

    private final String displayName;
    private final BigDecimal dailyRate;

    WardOption(String displayName, BigDecimal dailyRate) {
        this.displayName = displayName;
        this.dailyRate   = dailyRate;
    }

    public String getDisplayName() { return displayName; }
    public BigDecimal getDailyRate() { return dailyRate; }

    /** Convenience list for Thymeleaf templates — returns all ward options. */
    public static List<WardOption> getAll() {
        return Arrays.asList(values());
    }

    /**
     * Look up daily rate by display name (case-insensitive).
     * Returns null if the ward name does not match any option.
     */
    public static BigDecimal getRateForWard(String wardName) {
        if (wardName == null) return null;
        for (WardOption o : values()) {
            if (o.displayName.equalsIgnoreCase(wardName.trim())) {
                return o.dailyRate;
            }
        }
        return null;
    }
}
