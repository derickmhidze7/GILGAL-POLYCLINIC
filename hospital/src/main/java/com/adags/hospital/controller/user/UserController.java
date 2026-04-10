package com.adags.hospital.controller.user;

import com.adags.hospital.dto.user.ChangePasswordRequest;
import com.adags.hospital.dto.user.CreateUserRequest;
import com.adags.hospital.dto.user.UserResponse;
import com.adags.hospital.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserResponse>> getAll(
            @PageableDefault(size = 20, sort = "username") Pageable pageable) {
        return ResponseEntity.ok(userService.getAll(pageable).map(UserResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(UserResponse.from(userService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserResponse.from(userService.createUser(request)));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> toggleStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(UserResponse.from(userService.toggleStatus(id)));
    }

    @PatchMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resetPassword(
            @PathVariable UUID id,
            @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/change-password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        // UserService will look up the authenticated user's UUID from username
        // We pass null here and resolve in service via username
        userService.changePasswordByUsername(request, currentUser.getUsername());
        return ResponseEntity.noContent().build();
    }

    // Inner record for reset-password body
    public record ResetPasswordRequest(String newPassword) {}
}
