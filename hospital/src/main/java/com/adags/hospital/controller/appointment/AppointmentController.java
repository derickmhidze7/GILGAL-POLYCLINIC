package com.adags.hospital.controller.appointment;

import com.adags.hospital.domain.appointment.AppointmentStatus;
import com.adags.hospital.dto.appointment.AppointmentRequest;
import com.adags.hospital.dto.appointment.AppointmentResponse;
import com.adags.hospital.service.appointment.AppointmentService;
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
@RequestMapping("/appointments")
@RequiredArgsConstructor
@Tag(name = "Appointments", description = "Appointment scheduling and management")
public class AppointmentController {

    private final AppointmentService appointmentService;

    @GetMapping
    @PreAuthorize("hasAuthority('APPOINTMENT_READ')")
    @Operation(summary = "List all appointments (paginated)")
    public ResponseEntity<Page<AppointmentResponse>> getAll(
            @PageableDefault(size = 20, sort = "scheduledDateTime") Pageable pageable) {
        return ResponseEntity.ok(appointmentService.getAll(pageable));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAuthority('APPOINTMENT_READ')")
    @Operation(summary = "Get appointments for a specific patient")
    public ResponseEntity<Page<AppointmentResponse>> getByPatient(
            @PathVariable UUID patientId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(appointmentService.getByPatient(patientId, pageable));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAuthority('APPOINTMENT_READ')")
    @Operation(summary = "Filter appointments by status")
    public ResponseEntity<Page<AppointmentResponse>> getByStatus(
            @PathVariable AppointmentStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(appointmentService.getByStatus(status, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('APPOINTMENT_READ')")
    @Operation(summary = "Get appointment by ID")
    public ResponseEntity<AppointmentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(appointmentService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('APPOINTMENT_WRITE')")
    @Operation(summary = "Book a new appointment")
    public ResponseEntity<AppointmentResponse> create(@Valid @RequestBody AppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appointmentService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('APPOINTMENT_WRITE')")
    @Operation(summary = "Update an appointment")
    public ResponseEntity<AppointmentResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody AppointmentRequest request) {
        return ResponseEntity.ok(appointmentService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('APPOINTMENT_WRITE')")
    @Operation(summary = "Update appointment status (e.g., confirm, cancel, complete)")
    public ResponseEntity<AppointmentResponse> updateStatus(
            @PathVariable UUID id,
            @RequestParam AppointmentStatus status,
            @RequestParam(required = false) String cancellationReason) {
        return ResponseEntity.ok(appointmentService.updateStatus(id, status, cancellationReason));
    }
}
