package com.adags.hospital.dto.admin;

import com.adags.hospital.dto.patient.PatientResponse;

import java.math.BigDecimal;

/**
 * Carries patient info together with their aggregated billing totals
 * for the admin Patients page.
 */
public record PatientSpendingRow(
        PatientResponse patient,
        BigDecimal      totalBilled,
        long            invoiceCount) {}
