package com.adags.hospital.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 60, message = "Username must be 3-60 characters")
        String username,

        @NotBlank(message = "Password is required")
        String password
) {}
