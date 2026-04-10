package com.adags.hospital.dto.patient;

import com.adags.hospital.domain.common.Gender;
import com.adags.hospital.domain.patient.MaritalStatus;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record PatientRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 80)
        String firstName,

        @Size(max = 80)
        String middleName,

        @NotBlank(message = "Last name is required")
        @Size(max = 80)
        String lastName,

        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @NotNull(message = "Gender is required")
        Gender gender,

        MaritalStatus maritalStatus,

        @Size(max = 50, message = "National ID too long")
        String nationalId,

        @Size(max = 30)
        String phone,

        @Email(message = "Invalid email format")
        @Size(max = 150)
        String email,

        @Size(max = 100)
        String occupation,

        // Next of kin (required)
        @NotBlank(message = "Next of kin full name is required")
        String nextOfKinFullName,
        @NotBlank(message = "Next of kin relationship is required")
        String nextOfKinRelationship,
        @NotBlank(message = "Next of kin phone is required")
        String nextOfKinPhone,

        // Current location (optional)
        String currentStreet,
        String currentCity,

        // Permanent residence (optional)
        String permStreet,
        String permCity,

        // Insurance (optional)
        String insuranceProvider,
        String insurancePolicyNumber,
        String insuranceMemberNumber
) {}
