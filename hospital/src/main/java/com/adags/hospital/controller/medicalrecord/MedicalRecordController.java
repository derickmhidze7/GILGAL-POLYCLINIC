package com.adags.hospital.controller.medicalrecord;

import com.adags.hospital.dto.medicalrecord.MedicalRecordRequest;
import com.adags.hospital.dto.medicalrecord.MedicalRecordResponse;
import com.adags.hospital.service.medicalrecord.MedicalRecordService;
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
@RequestMapping("/medical-records")
@RequiredArgsConstructor
@Tag(name = "Medical Records", description = "Patient visit records, diagnoses, and prescriptions")
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAuthority('MEDICAL_RECORD_READ')")
    @Operation(summary = "Get all medical records for a patient")
    public ResponseEntity<Page<MedicalRecordResponse>> getByPatient(
            @PathVariable UUID patientId,
            @PageableDefault(size = 20, sort = "visitDate") Pageable pageable) {
        return ResponseEntity.ok(medicalRecordService.getByPatient(patientId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MEDICAL_RECORD_READ')")
    @Operation(summary = "Get a medical record by ID")
    public ResponseEntity<MedicalRecordResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(medicalRecordService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MEDICAL_RECORD_WRITE')")
    @Operation(summary = "Create a new medical record for a visit")
    public ResponseEntity<MedicalRecordResponse> create(@Valid @RequestBody MedicalRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(medicalRecordService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MEDICAL_RECORD_WRITE')")
    @Operation(summary = "Update a medical record")
    public ResponseEntity<MedicalRecordResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody MedicalRecordRequest request) {
        return ResponseEntity.ok(medicalRecordService.update(id, request));
    }
}
