package com.adags.hospital.domain.user;

/**
 * Fine-grained permissions used with @PreAuthorize("hasAuthority('...')")
 */
public enum Permission {

    // User management
    USER_READ,
    USER_WRITE,

    // Staff management
    STAFF_READ,
    STAFF_WRITE,

    // Patient management
    PATIENT_READ,
    PATIENT_WRITE,

    // Appointments
    APPOINTMENT_READ,
    APPOINTMENT_WRITE,

    // Triage
    TRIAGE_READ,
    TRIAGE_WRITE,

    // Medical records
    MEDICAL_RECORD_READ,
    MEDICAL_RECORD_WRITE,

    // Lab
    LAB_REQUEST_READ,
    LAB_REQUEST_WRITE,
    LAB_RESULT_READ,
    LAB_RESULT_WRITE,

    // Pharmacy
    PHARMACY_READ,
    PHARMACY_WRITE,
    DISPENSE_MEDICATION,

    // Billing
    BILLING_READ,
    BILLING_WRITE,
    PAYMENT_WRITE,

    // Reports
    REPORT_VIEW
}
