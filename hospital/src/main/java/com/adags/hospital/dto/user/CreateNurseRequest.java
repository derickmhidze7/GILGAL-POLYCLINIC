package com.adags.hospital.dto.user;

import com.adags.hospital.domain.common.Gender;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.UUID;

public record CreateNurseRequest(

        // ── Staff personal details ────────────────────────────────────────────
        @NotBlank(message = "First name is required")
        @Size(max = 80, message = "First name must be at most 80 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 80, message = "Last name must be at most 80 characters")
        String lastName,

        LocalDate dateOfBirth,

        Gender gender,

        @Size(max = 30, message = "Phone number must be at most 30 characters")
        String phone,

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email,

        UUID departmentId,

        @Size(max = 80, message = "License number must be at most 80 characters")
        String licenseNumber,

        LocalDate employmentDate,

        // ── Login account details ─────────────────────────────────────────────
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 60, message = "Username must be 3–60 characters")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password

) {}
