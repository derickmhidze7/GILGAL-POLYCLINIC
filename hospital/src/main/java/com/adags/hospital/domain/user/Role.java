package com.adags.hospital.domain.user;

import java.util.Set;

/**
 * System roles. Each role owns a fixed set of Permissions that are
 * returned as GrantedAuthority entries by CustomUserDetailsService.
 */
public enum Role {

    ADMIN {
        @Override
        public Set<Permission> getPermissions() {
            return Set.of(Permission.values()); // all permissions
        }
    },

    RECEPTIONIST {
        @Override
        public Set<Permission> getPermissions() {
            return Set.of(
                    Permission.PATIENT_READ, Permission.PATIENT_WRITE,
                    Permission.APPOINTMENT_READ, Permission.APPOINTMENT_WRITE,
                    Permission.STAFF_READ,
                    Permission.BILLING_READ,
                    Permission.TRIAGE_READ
            );
        }
    },

    TRIAGE_NURSE {
        @Override
        public Set<Permission> getPermissions() {
            return Set.of(
                    Permission.PATIENT_READ,
                    Permission.APPOINTMENT_READ,
                    Permission.TRIAGE_READ, Permission.TRIAGE_WRITE,
                    Permission.MEDICAL_RECORD_READ
            );
        }
    },

    DOCTOR {
        @Override
        public Set<Permission> getPermissions() {
            return Set.of(
                    Permission.PATIENT_READ,
                    Permission.APPOINTMENT_READ, Permission.APPOINTMENT_WRITE,
                    Permission.TRIAGE_READ,
                    Permission.MEDICAL_RECORD_READ, Permission.MEDICAL_RECORD_WRITE,
                    Permission.LAB_REQUEST_READ, Permission.LAB_REQUEST_WRITE,
                    Permission.LAB_RESULT_READ,
                    Permission.PHARMACY_READ,
                    Permission.STAFF_READ,
                    Permission.REPORT_VIEW
            );
        }
    },

    SPECIALIST_DOCTOR {
        @Override
        public Set<Permission> getPermissions() {
            return Set.of(
                    Permission.PATIENT_READ,
                    Permission.APPOINTMENT_READ, Permission.APPOINTMENT_WRITE,
                    Permission.TRIAGE_READ,
                    Permission.MEDICAL_RECORD_READ, Permission.MEDICAL_RECORD_WRITE,
                    Permission.LAB_REQUEST_READ, Permission.LAB_REQUEST_WRITE,
                    Permission.LAB_RESULT_READ,
                    Permission.PHARMACY_READ,
                    Permission.STAFF_READ,
                    Permission.REPORT_VIEW
            );
        }
    },

    LAB_TECHNICIAN {
        @Override
        public Set<Permission> getPermissions() {
            return Set.of(
                    Permission.PATIENT_READ,
                    Permission.LAB_REQUEST_READ,
                    Permission.LAB_RESULT_READ, Permission.LAB_RESULT_WRITE,
                    Permission.MEDICAL_RECORD_READ,
                    Permission.REPORT_VIEW
            );
        }
    },

    PHARMACIST {
        @Override
        public Set<Permission> getPermissions() {
            return Set.of(
                    Permission.PATIENT_READ,
                    Permission.PHARMACY_READ, Permission.PHARMACY_WRITE,
                    Permission.DISPENSE_MEDICATION,
                    Permission.MEDICAL_RECORD_READ,
                    Permission.REPORT_VIEW
            );
        }
    },

    ACCOUNTANT {
        @Override
        public Set<Permission> getPermissions() {
            return Set.of(
                    Permission.PATIENT_READ,
                    Permission.BILLING_READ, Permission.BILLING_WRITE,
                    Permission.PAYMENT_WRITE,
                    Permission.PHARMACY_READ,
                    Permission.STAFF_READ,
                    Permission.REPORT_VIEW
            );
        }
    },

    WARD_NURSE {
        @Override
        public Set<Permission> getPermissions() {
            return Set.of(
                    Permission.PATIENT_READ,
                    Permission.MEDICAL_RECORD_READ,
                    Permission.TRIAGE_READ,
                    Permission.LAB_RESULT_READ,
                    Permission.PHARMACY_READ
            );
        }
    },

    /**
     * General Nurse — can perform both triage assessments (like TRIAGE_NURSE)
     * AND ward / surgical patient care (like WARD_NURSE) depending on assignment.
     */
    NURSE {
        @Override
        public Set<Permission> getPermissions() {
            return Set.of(
                    Permission.PATIENT_READ,
                    Permission.APPOINTMENT_READ,
                    Permission.TRIAGE_READ, Permission.TRIAGE_WRITE,
                    Permission.MEDICAL_RECORD_READ,
                    Permission.LAB_RESULT_READ,
                    Permission.PHARMACY_READ
            );
        }
    };

    public abstract Set<Permission> getPermissions();
}
