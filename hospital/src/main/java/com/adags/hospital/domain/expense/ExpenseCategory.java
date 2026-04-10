package com.adags.hospital.domain.expense;

public enum ExpenseCategory {
    MEDICAL_SUPPLIES("Medical Supplies"),
    EQUIPMENT("Equipment"),
    SALARIES("Salaries"),
    MAINTENANCE("Maintenance"),
    CLEANING("Cleaning"),
    LAB_SUPPLIES("Laboratory Supplies"),
    PHARMACY_RESTOCK("Pharmacy Restock"),
    TRANSPORT("Transport"),
    UTILITIES("Utilities"),
    MISCELLANEOUS("Miscellaneous");

    private final String label;

    ExpenseCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
