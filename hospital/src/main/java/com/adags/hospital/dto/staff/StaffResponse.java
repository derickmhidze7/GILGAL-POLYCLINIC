package com.adags.hospital.dto.staff;

import com.adags.hospital.domain.common.Gender;
import com.adags.hospital.domain.user.Role;

import java.time.LocalDate;
import java.util.UUID;

public record StaffResponse(
        UUID id,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        Gender gender,
        String phone,
        String email,
        UUID departmentId,
        String departmentName,
        Role staffRole,
        String specialization,
        String licenseNumber,
        LocalDate employmentDate,
        boolean active
) {}
