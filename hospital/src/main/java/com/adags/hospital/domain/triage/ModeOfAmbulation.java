package com.adags.hospital.domain.triage;

/**
 * Describes how the patient arrived / their current mode of movement.
 */
public enum ModeOfAmbulation {
    WALKING("Walking"),
    INFANT_IN_MOTHERS_LAP("Infant in mother's lap"),
    WHEELCHAIR_ASSISTED("Assisted on wheelchair"),
    STRETCHER_ASSISTED("Assisted on stretcher");

    private final String displayName;

    ModeOfAmbulation(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
