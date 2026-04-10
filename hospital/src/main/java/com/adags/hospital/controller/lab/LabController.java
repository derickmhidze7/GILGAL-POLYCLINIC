package com.adags.hospital.controller.lab;

import com.adags.hospital.domain.lab.LabRequest;
import com.adags.hospital.domain.lab.LabResult;
import com.adags.hospital.dto.lab.LabRequestDto;
import com.adags.hospital.dto.lab.LabResultDto;
import com.adags.hospital.service.lab.LabService;
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
@RequestMapping("/lab")
@RequiredArgsConstructor
@Tag(name = "Laboratory", description = "Lab requests and results management")
public class LabController {

    private final LabService labService;

    @GetMapping("/requests/pending")
    @PreAuthorize("hasAuthority('LAB_REQUEST_READ')")
    @Operation(summary = "Get all pending lab requests")
    public ResponseEntity<Page<LabRequest>> getPendingRequests(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(labService.getPendingRequests(pageable));
    }

    @GetMapping("/requests/patient/{patientId}")
    @PreAuthorize("hasAuthority('LAB_REQUEST_READ')")
    @Operation(summary = "Get lab requests for a patient")
    public ResponseEntity<Page<LabRequest>> getByPatient(
            @PathVariable UUID patientId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(labService.getByPatient(patientId, pageable));
    }

    @PostMapping("/requests")
    @PreAuthorize("hasAuthority('LAB_REQUEST_WRITE')")
    @Operation(summary = "Create a lab test request")
    public ResponseEntity<LabRequest> createRequest(@Valid @RequestBody LabRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(labService.createRequest(dto));
    }

    @PostMapping("/results")
    @PreAuthorize("hasAuthority('LAB_RESULT_WRITE')")
    @Operation(summary = "Record lab test results")
    public ResponseEntity<LabResult> addResult(@Valid @RequestBody LabResultDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(labService.addResult(dto));
    }
}
