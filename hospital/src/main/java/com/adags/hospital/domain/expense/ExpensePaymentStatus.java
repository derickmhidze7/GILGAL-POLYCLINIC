package com.adags.hospital.domain.expense;

public enum ExpensePaymentStatus {
    PAID("Paid"),
    UNPAID("Unpaid"),
    OVERDUE("Overdue"),
    PENDING("Pending");

    private final String label;

    ExpensePaymentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
