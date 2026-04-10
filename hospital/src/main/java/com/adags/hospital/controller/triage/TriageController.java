package com.adags.hospital.controller.triage;

import com.adags.hospital.dto.triage.TriageRequest;
import com.adags.hospital.dto.triage.TriageResponse;
import com.adags.hospital.service.triage.TriageService;
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
@RequestMapping("/triage")
@RequiredArgsConstructor
@Tag(name = "Triage", description = "Patient triage assessments and vitals")
public class TriageController {

    private final TriageService triageService;

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAuthority('TRIAGE_READ')")
    @Operation(summary = "Get all triage assessments for a patient")
    public ResponseEntity<Page<TriageResponse>> getByPatient(
            @PathVariable UUID patientId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(triageService.getByPatient(patientId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TRIAGE_READ')")
    @Operation(summary = "Get a triage assessment by ID")
    public ResponseEntity<TriageResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(triageService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TRIAGE_WRITE')")
    @Operation(summary = "Record a triage assessment")
    public ResponseEntity<TriageResponse> create(@Valid @RequestBody TriageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(triageService.create(request));
    }
}
