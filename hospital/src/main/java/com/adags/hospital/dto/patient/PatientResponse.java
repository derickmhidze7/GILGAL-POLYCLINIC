package com.adags.hospital.dto.patient;

import com.adags.hospital.domain.common.Gender;
import com.adags.hospital.domain.patient.MaritalStatus;

import java.time.LocalDate;
import java.util.UUID;

public record PatientResponse(
        UUID id,
        String firstName,
        String middleName,
        String lastName,
        LocalDate dateOfBirth,
        int age,
        Gender gender,
        MaritalStatus maritalStatus,
        String nationalId,
        String phone,
        String email,
        String occupation,
        LocalDate registrationDate,
        boolean active,
        // Current address
        String addressStreet,
        String addressCity,
        String addressProvince,
        String addressCountry,
        String addressPostalCode,
        // Permanent address
        String permStreet,
        String permCity,
        String permProvince,
        String permCountry,
        String permPostalCode,
        // Next of kin
        String nokFullName,
        String nokRelationship,
        String nokPhone,
        String nokEmail,
        // Insurance
        String insuranceProvider,
        String insurancePolicyNumber,
        String insuranceMemberNumber
) {}
