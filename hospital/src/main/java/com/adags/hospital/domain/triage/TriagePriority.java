package com.adags.hospital.domain.triage;

public enum TriagePriority {
    /** Life-threatening — immediate intervention required */
    IMMEDIATE,
    /** Serious condition — urgent but stable */
    URGENT,
    /** Less urgent — can wait a short time */
    LESS_URGENT,
    /** Non-urgent — minor issue */
    NON_URGENT
}
