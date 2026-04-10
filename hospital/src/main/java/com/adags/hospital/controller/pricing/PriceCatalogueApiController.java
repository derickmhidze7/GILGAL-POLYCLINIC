package com.adags.hospital.controller.pricing;

import com.adags.hospital.dto.pricing.ExcelUploadResult;
import com.adags.hospital.dto.pricing.ServicePriceItemRequest;
import com.adags.hospital.dto.pricing.ServicePriceItemResponse;
import com.adags.hospital.service.pricing.PriceCatalogueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/price-catalogue")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PriceCatalogueApiController {

    private final PriceCatalogueService service;

    @GetMapping
    public ResponseEntity<List<ServicePriceItemResponse>> getAll(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search) {
        if (search != null && !search.isBlank()) {
            return ResponseEntity.ok(service.search(search));
        }
        if (type != null && !type.isBlank()) {
            return ResponseEntity.ok(service.getByType(type));
        }
        return ResponseEntity.ok(service.getAll());
    }

    @PostMapping
    public ResponseEntity<ServicePriceItemResponse> create(@Valid @RequestBody ServicePriceItemRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServicePriceItemResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ServicePriceItemRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/upload-excel")
    public ResponseEntity<ExcelUploadResult> uploadExcel(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            return ResponseEntity.badRequest().build();
        }
        try {
            ExcelUploadResult result = service.importFromExcel(file);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/types")
    public ResponseEntity<List<String>> getTypes() {
        return ResponseEntity.ok(service.getDistinctTypes());
    }
}
