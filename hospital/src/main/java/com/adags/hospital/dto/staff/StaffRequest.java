package com.adags.hospital.dto.staff;

import com.adags.hospital.domain.common.Gender;
import com.adags.hospital.domain.user.Role;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.UUID;

public record StaffRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 80)
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 80)
        String lastName,

        LocalDate dateOfBirth,

        Gender gender,

        @Pattern(regexp = "^[+]?[0-9\\s\\-]{7,20}$", message = "Invalid phone number")
        String phone,

        @NotBlank(message = "Email is required")
        @Email
        @Size(max = 150)
        String email,

        UUID departmentId,

        @NotNull(message = "Role is required")
        Role staffRole,

        String specialization,

        String licenseNumber,

        LocalDate employmentDate
) {}
