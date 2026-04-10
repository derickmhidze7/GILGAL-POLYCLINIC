package com.adags.hospital.controller.pharmacy;

import com.adags.hospital.domain.pharmacy.InventoryItem;
import com.adags.hospital.domain.pharmacy.Medication;
import com.adags.hospital.service.pharmacy.PharmacyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pharmacy")
@RequiredArgsConstructor
@Tag(name = "Pharmacy", description = "Medication catalog, inventory and dispensing")
public class PharmacyController {

    private final PharmacyService pharmacyService;

    @GetMapping("/medications")
    @PreAuthorize("hasAuthority('PHARMACY_READ')")
    @Operation(summary = "List all active medications")
    public ResponseEntity<Page<Medication>> getMedications(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(pharmacyService.getMedications(pageable));
    }

    @GetMapping("/medications/search")
    @PreAuthorize("hasAuthority('PHARMACY_READ')")
    @Operation(summary = "Search medications by generic or brand name")
    public ResponseEntity<Page<Medication>> searchMedications(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(pharmacyService.searchMedications(q, pageable));
    }

    @PostMapping("/medications")
    @PreAuthorize("hasAuthority('PHARMACY_WRITE')")
    @Operation(summary = "Add a new medication to the catalog")
    public ResponseEntity<Medication> addMedication(@RequestBody Medication medication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pharmacyService.addMedication(medication));
    }

    @GetMapping("/inventory")
    @PreAuthorize("hasAuthority('PHARMACY_READ')")
    @Operation(summary = "View inventory for a medication")
    public ResponseEntity<Page<InventoryItem>> getInventory(
            @RequestParam UUID medicationId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(pharmacyService.getInventory(medicationId, pageable));
    }

    @GetMapping("/inventory/low-stock")
    @PreAuthorize("hasAuthority('PHARMACY_READ')")
    @Operation(summary = "Get all items below reorder level")
    public ResponseEntity<List<InventoryItem>> getLowStock() {
        return ResponseEntity.ok(pharmacyService.getLowStockItems());
    }

    @PostMapping("/inventory")
    @PreAuthorize("hasAuthority('PHARMACY_WRITE')")
    @Operation(summary = "Add inventory stock for a medication")
    public ResponseEntity<InventoryItem> addInventory(@RequestBody InventoryItem item) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pharmacyService.addInventory(item));
    }
}
