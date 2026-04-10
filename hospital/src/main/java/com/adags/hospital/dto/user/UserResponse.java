package com.adags.hospital.dto.user;

import com.adags.hospital.domain.user.AppUser;
import com.adags.hospital.domain.user.Role;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        Role role,
        boolean enabled,
        boolean accountNonLocked,
        UUID staffId,
        LocalDateTime createdAt
) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.isAccountNonLocked(),
                user.getStaff() != null ? user.getStaff().getId() : null,
                user.getCreatedAt()
        );
    }
}
