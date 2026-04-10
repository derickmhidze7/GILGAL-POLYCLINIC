package com.adags.hospital.controller.staff;

import com.adags.hospital.dto.staff.StaffRequest;
import com.adags.hospital.dto.staff.StaffResponse;
import com.adags.hospital.service.staff.StaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/staff")
@RequiredArgsConstructor
@Tag(name = "Staff", description = "Staff and department management")
public class StaffController {

    private final StaffService staffService;

    @GetMapping
    @PreAuthorize("hasAuthority('STAFF_READ')")
    @Operation(summary = "List all active staff members")
    public ResponseEntity<Page<StaffResponse>> getAll(
            @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {
        return ResponseEntity.ok(staffService.getAll(pageable));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('STAFF_READ')")
    @Operation(summary = "Search staff by name or specialization")
    public ResponseEntity<Page<StaffResponse>> search(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(staffService.search(q, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STAFF_READ')")
    @Operation(summary = "Get a staff member by ID")
    public ResponseEntity<StaffResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(staffService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('STAFF_WRITE')")
    @Operation(summary = "Add a new staff member")
    public ResponseEntity<StaffResponse> create(@Valid @RequestBody StaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('STAFF_WRITE')")
    @Operation(summary = "Update a staff member")
    public ResponseEntity<StaffResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody StaffRequest request) {
        return ResponseEntity.ok(staffService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a staff member (soft delete)")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        staffService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
