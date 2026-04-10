package com.adags.hospital.domain.patient;

public enum BloodGroup {
    A_POSITIVE("A+"),
    A_NEGATIVE("A-"),
    B_POSITIVE("B+"),
    B_NEGATIVE("B-"),
    AB_POSITIVE("AB+"),
    AB_NEGATIVE("AB-"),
    O_POSITIVE("O+"),
    O_NEGATIVE("O-"),
    UNKNOWN("Unknown");

    private final String display;

    BloodGroup(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }
}
