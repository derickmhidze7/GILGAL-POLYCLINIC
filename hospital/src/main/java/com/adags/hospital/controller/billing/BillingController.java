package com.adags.hospital.controller.billing;

import com.adags.hospital.domain.billing.Invoice;
import com.adags.hospital.domain.billing.Payment;
import com.adags.hospital.domain.billing.PaymentMethod;
import com.adags.hospital.dto.billing.InvoiceRequest;
import com.adags.hospital.service.billing.BillingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('BILLING_READ')")
    public ResponseEntity<Page<Invoice>> getAllInvoices(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(billingService.getAll(pageable));
    }

    @GetMapping("/invoices/patient/{patientId}")
    @PreAuthorize("hasAuthority('BILLING_READ')")
    public ResponseEntity<Page<Invoice>> getByPatient(
            @PathVariable UUID patientId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(billingService.getByPatient(patientId, pageable));
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAuthority('BILLING_READ')")
    public ResponseEntity<Invoice> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(billingService.getById(id));
    }

    @PostMapping("/invoices")
    @PreAuthorize("hasAuthority('BILLING_WRITE')")
    public ResponseEntity<Invoice> createInvoice(@Valid @RequestBody InvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(billingService.createInvoice(request));
    }

    @PatchMapping("/invoices/{id}/issue")
    @PreAuthorize("hasAuthority('BILLING_WRITE')")
    public ResponseEntity<Invoice> issueInvoice(@PathVariable UUID id) {
        return ResponseEntity.ok(billingService.issueInvoice(id));
    }

    @PostMapping("/invoices/{id}/payments")
    @PreAuthorize("hasAuthority('PAYMENT_WRITE')")
    public ResponseEntity<Payment> recordPayment(
            @PathVariable UUID id,
            @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(billingService.recordPayment(id, request.amount(), request.method(), request.reference()));
    }

    // Inner record for payment request body
    public record PaymentRequest(
            @NotNull BigDecimal amount,
            @NotNull PaymentMethod method,
            String reference
    ) {}
}
