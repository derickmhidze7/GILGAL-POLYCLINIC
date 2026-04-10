package com.adags.hospital.service.billing;

import com.adags.hospital.domain.billing.Invoice;
import com.adags.hospital.domain.billing.InvoiceLineItem;
import com.adags.hospital.domain.billing.InvoiceStatus;
import com.adags.hospital.domain.billing.LineItemCategory;
import com.adags.hospital.domain.billing.Payment;
import com.adags.hospital.domain.billing.PaymentMethod;
import com.adags.hospital.domain.medicalrecord.MedicalRecord;
import com.adags.hospital.domain.medicalrecord.PrescriptionPharmacyStatus;
import com.adags.hospital.domain.patient.Patient;
import com.adags.hospital.domain.user.AppUser;
import com.adags.hospital.dto.billing.InvoiceRequest;
import com.adags.hospital.exception.BusinessRuleException;
import com.adags.hospital.exception.ResourceNotFoundException;
import com.adags.hospital.repository.billing.InvoiceRepository;
import com.adags.hospital.repository.medicalrecord.MedicalRecordRepository;
import com.adags.hospital.repository.medicalrecord.PrescriptionRepository;
import com.adags.hospital.repository.patient.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingService {

    private final InvoiceRepository invoiceRepository;
    private final PatientRepository patientRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final PrescriptionRepository prescriptionRepository;

    public Page<Invoice> getAll(Pageable pageable) {
        return invoiceRepository.findAll(pageable);
    }

    /** Returns a page of invoices with patient and line-items eagerly fetched,
     *  safe to use when spring.jpa.open-in-view=false. */
    public Page<Invoice> getAllWithDetails(Pageable pageable) {
        return invoiceRepository.findAllWithDetails(pageable);
    }

    public Page<Invoice> getByPatient(UUID patientId, Pageable pageable) {
        return invoiceRepository.findByPatientId(patientId, pageable);
    }

    public Invoice getById(UUID id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", id));
    }

    @Transactional
    public Invoice createInvoice(InvoiceRequest request) {
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", request.patientId()));

        MedicalRecord record = null;
        if (request.medicalRecordId() != null) {
            record = medicalRecordRepository.findById(request.medicalRecordId())
                    .orElseThrow(() -> new ResourceNotFoundException("MedicalRecord", "id", request.medicalRecordId()));
        }

        String invoiceNumber = "INV-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .format(LocalDateTime.now());

        Invoice invoice = Invoice.builder()
                .patient(patient)
                .medicalRecord(record)
                .invoiceNumber(invoiceNumber)
                .dueDate(request.dueDate())
                .notes(request.notes())
                .build();

        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice issueInvoice(UUID id) {
        Invoice invoice = getById(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new BusinessRuleException("Only DRAFT invoices can be issued");
        }
        invoice.setStatus(InvoiceStatus.ISSUED);
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Payment recordPayment(UUID invoiceId, BigDecimal amount, PaymentMethod method, String reference) {
        Invoice invoice = getById(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID
                || invoice.getStatus() == InvoiceStatus.VOIDED
                || invoice.getStatus() == InvoiceStatus.CANCELLATION_PENDING
                || invoice.getStatus() == InvoiceStatus.TERMINATED) {
            throw new BusinessRuleException("Invoice is already " + invoice.getStatus());
        }

        Payment payment = Payment.builder()
                .invoice(invoice)
                .amountPaid(amount)
                .paymentMethod(method)
                .referenceNumber(reference)
                .build();

        invoice.getPayments().add(payment);

        BigDecimal totalPaid = invoice.getPayments().stream()
                .map(Payment::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPaid.compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }

        invoiceRepository.save(invoice);

        // When a PHARMACY invoice is fully paid, release its prescriptions to the pharmacist queue
        if (invoice.getStatus() == InvoiceStatus.PAID && invoice.getMedicalRecord() != null) {
            boolean hasPharmacyItems = invoice.getLineItems().stream()
                    .anyMatch(li -> li.getCategory() == LineItemCategory.PHARMACY);
            if (hasPharmacyItems) {
                prescriptionRepository.findByMedicalRecordId(invoice.getMedicalRecord().getId())
                        .stream()
                        .filter(px -> px.getPharmacyStatus() == PrescriptionPharmacyStatus.AWAITING_PAYMENT)
                        .forEach(px -> {
                            px.setPharmacyStatus(PrescriptionPharmacyStatus.READY_TO_DISPENSE);
                            prescriptionRepository.save(px);
                        });
            }
        }

        return payment;
    }

    /**
     * Creates and immediately issues a consultation invoice (DRAFT → ISSUED).
     * Used by the triage service when referring a patient to a doctor.
     */
    @Transactional
    public Invoice createConsultationInvoice(UUID patientId, String description, BigDecimal fee) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId));

        String invoiceNumber = "INV-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .format(LocalDateTime.now());

        Invoice invoice = Invoice.builder()
                .patient(patient)
                .invoiceNumber(invoiceNumber)
                .dueDate(java.time.LocalDate.now())
                .notes(description)
                .subtotal(fee)
                .totalAmount(fee)
                .status(InvoiceStatus.ISSUED)
                .build();

        InvoiceLineItem lineItem = InvoiceLineItem.builder()
                .invoice(invoice)
                .description(description)
                .category(LineItemCategory.CONSULTATION)
                .quantity(1)
                .unitPrice(fee)
                .lineTotal(fee)
                .build();

        invoice.getLineItems().add(lineItem);
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice addLineItemToInvoice(UUID invoiceId, String description,
                                         LineItemCategory category, int quantity, BigDecimal unitPrice) {
        Invoice invoice = getById(invoiceId);
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        InvoiceLineItem item = InvoiceLineItem.builder()
                .invoice(invoice)
                .description(description)
                .category(category)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .lineTotal(lineTotal)
                .build();
        invoice.getLineItems().add(item);
        BigDecimal newSubtotal = invoice.getSubtotal().add(lineTotal);
        invoice.setSubtotal(newSubtotal);
        invoice.setTotalAmount(newSubtotal.add(invoice.getTaxAmount()));
        return invoiceRepository.save(invoice);
    }

    // ── Cancellation management ─────────────────────────────────────────────

    /**
     * Requests cancellation of an invoice. Allowed for ISSUED and PAID invoices only.
     * Sets status to CANCELLATION_PENDING and records the reason, requester, and timestamp.
     */
    @Transactional
    public Invoice requestCancellation(UUID invoiceId, String reason, AppUser requestedBy) {
        Invoice invoice = getById(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.ISSUED && invoice.getStatus() != InvoiceStatus.PAID) {
            throw new BusinessRuleException(
                    "Only ISSUED or PAID invoices can be submitted for cancellation. Current status: "
                            + invoice.getStatus());
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("A cancellation reason is required.");
        }
        invoice.setStatus(InvoiceStatus.CANCELLATION_PENDING);
        invoice.setCancellationReason(reason.strip());
        invoice.setCancellationRequestedBy(requestedBy);
        invoice.setCancellationRequestedAt(java.time.LocalDateTime.now());
        return invoiceRepository.save(invoice);
    }

    /**
     * Admin approves the cancellation request → invoice becomes VOIDED.
     */
    @Transactional
    public Invoice approveCancellation(UUID invoiceId) {
        Invoice invoice = getById(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.CANCELLATION_PENDING) {
            throw new BusinessRuleException("Invoice is not pending cancellation.");
        }
        invoice.setStatus(InvoiceStatus.VOIDED);
        return invoiceRepository.save(invoice);
    }

    /**
     * Admin rejects the cancellation request → invoice becomes TERMINATED.
     */
    @Transactional
    public Invoice rejectCancellation(UUID invoiceId) {
        Invoice invoice = getById(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.CANCELLATION_PENDING) {
            throw new BusinessRuleException("Invoice is not pending cancellation.");
        }
        invoice.setStatus(InvoiceStatus.TERMINATED);
        return invoiceRepository.save(invoice);
    }

    /** Returns the number of invoices currently awaiting admin cancellation approval. */
    public long countPendingCancellations() {
        return invoiceRepository.countByStatus(InvoiceStatus.CANCELLATION_PENDING);
    }

    /** Returns all invoices with CANCELLATION_PENDING status, including patient and requester. */
    public java.util.List<Invoice> getPendingCancellations() {
        return invoiceRepository.findAllByStatusFetchDetails(InvoiceStatus.CANCELLATION_PENDING);
    }

    /** Permanently deletes a VOIDED invoice. Only allowed when status is VOIDED. */
    @Transactional
    public void deleteVoidedInvoice(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));
        if (invoice.getStatus() != InvoiceStatus.VOIDED) {
            throw new BusinessRuleException("Only VOIDED invoices can be deleted.");
        }
        invoiceRepository.delete(invoice);
    }
}
