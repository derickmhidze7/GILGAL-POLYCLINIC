package com.adags.hospital.dto.user;

import com.adags.hospital.domain.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateUserRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 60, message = "Username must be 3–60 characters")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotNull(message = "Role is required")
        Role role,

        UUID staffId
) {}
