package com.adags.hospital.controller.patient;

import com.adags.hospital.dto.patient.PatientRequest;
import com.adags.hospital.dto.patient.PatientResponse;
import com.adags.hospital.service.patient.PatientService;
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
@RequestMapping("/patients")
@RequiredArgsConstructor
@Tag(name = "Patients", description = "Patient registration and management")
public class PatientController {

    private final PatientService patientService;

    @GetMapping
    @PreAuthorize("hasAuthority('PATIENT_READ')")
    @Operation(summary = "List all active patients (paginated)")
    public ResponseEntity<Page<PatientResponse>> getAll(
            @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {
        return ResponseEntity.ok(patientService.getAll(pageable));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('PATIENT_READ')")
    @Operation(summary = "Search patients by name, national ID, or phone")
    public ResponseEntity<Page<PatientResponse>> search(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(patientService.search(q, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PATIENT_READ')")
    @Operation(summary = "Get a patient by ID")
    public ResponseEntity<PatientResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(patientService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PATIENT_WRITE')")
    @Operation(summary = "Register a new patient")
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody PatientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(patientService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PATIENT_WRITE')")
    @Operation(summary = "Update patient details")
    public ResponseEntity<PatientResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody PatientRequest request) {
        return ResponseEntity.ok(patientService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a patient record (soft delete)")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        patientService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
