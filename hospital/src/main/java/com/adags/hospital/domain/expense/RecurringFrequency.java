package com.adags.hospital.domain.expense;

public enum RecurringFrequency {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    ANNUALLY("Annually");

    private final String label;

    RecurringFrequency(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
