package com.adags.hospital.dto.auth;

import com.adags.hospital.domain.user.Role;

public record JwtResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInMs,
        String username,
        Role role
) {
    public static JwtResponse of(String accessToken, String refreshToken,
                                  long expiresInMs, String username, Role role) {
        return new JwtResponse(accessToken, refreshToken, "Bearer", expiresInMs, username, role);
    }
}
