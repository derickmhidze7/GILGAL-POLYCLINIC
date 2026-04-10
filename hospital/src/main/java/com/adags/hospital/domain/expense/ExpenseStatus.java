package com.adags.hospital.domain.expense;

public enum ExpenseStatus {
    DRAFT("Draft"),
    PENDING_APPROVAL("Pending Approval"),
    APPROVED("Approved"),
    REJECTED("Rejected");

    private final String label;

    ExpenseStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
