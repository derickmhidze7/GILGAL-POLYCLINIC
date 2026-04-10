package com.adags.hospital.repository.billing;

import com.adags.hospital.domain.billing.Invoice;
import com.adags.hospital.domain.billing.InvoiceStatus;
import com.adags.hospital.domain.billing.InvoiceLineItem;
import com.adags.hospital.domain.billing.LineItemCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    Page<Invoice> findByPatientId(UUID patientId, Pageable pageable);

    Page<Invoice> findByStatus(InvoiceStatus status, Pageable pageable);

    boolean existsByInvoiceNumber(String invoiceNumber);

    @Query("SELECT i FROM Invoice i JOIN FETCH i.patient")
    List<Invoice> findAllFetchPatient(Pageable pageable);

    /** Billing page — fetches patient + all line items in one query so the template
     *  can inspect categories without triggering lazy-load errors. */
    @Query("SELECT DISTINCT i FROM Invoice i JOIN FETCH i.patient LEFT JOIN FETCH i.lineItems ORDER BY i.createdAt DESC")
    List<Invoice> findAllFetchPatientAndLineItems();

    /** Pageable version — eagerly loads patient + lineItems so Thymeleaf can render
     *  them safely with spring.jpa.open-in-view=false. */
    @Query(value = "SELECT DISTINCT i FROM Invoice i JOIN FETCH i.patient LEFT JOIN FETCH i.lineItems",
           countQuery = "SELECT COUNT(DISTINCT i) FROM Invoice i")
    Page<Invoice> findAllWithDetails(Pageable pageable);

    @Query("SELECT i FROM Invoice i JOIN FETCH i.patient WHERE i.patient.id = :patientId")
    List<Invoice> findByPatientIdFetchPatient(@Param("patientId") UUID patientId, Pageable pageable);

    /** Returns [invoiceId, SUM(amountPaid)] for all invoices whose IDs are in the given list. */
    @Query("SELECT p.invoice.id, SUM(p.amountPaid) FROM Payment p WHERE p.invoice.id IN :ids GROUP BY p.invoice.id")
    List<Object[]> sumPaymentsByInvoiceIds(@Param("ids") List<UUID> ids);

    @Query("SELECT i FROM Invoice i WHERE i.medicalRecord.id = :medicalRecordId")
    List<Invoice> findByMedicalRecordId(@Param("medicalRecordId") UUID medicalRecordId);

    // ── Revenue analytics ────────────────────────────────────────────────────

    /**
     * Returns [LineItemCategory, SUM(lineTotal), COUNT(DISTINCT invoiceId)] per category
     * for invoices whose status is in {@code statuses} and whose invoiceDate falls
     * within [from, to] (inclusive).
     */
    @Query("SELECT li.category, SUM(li.lineTotal), COUNT(DISTINCT li.invoice.id) " +
           "FROM InvoiceLineItem li " +
           "WHERE li.invoice.status IN :statuses " +
           "AND li.invoice.invoiceDate >= :from " +
           "AND li.invoice.invoiceDate <= :to " +
           "GROUP BY li.category")
    List<Object[]> revenueByCategoryBetween(
            @Param("statuses") Collection<InvoiceStatus> statuses,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Returns [month (1-12), SUM(lineTotal)] for each month containing at least one
     * matching line item. Use a full-year range for [from, to] to get all 12 months.
     */
    @Query("SELECT EXTRACT(MONTH FROM li.invoice.invoiceDate), SUM(li.lineTotal) " +
           "FROM InvoiceLineItem li " +
           "WHERE li.invoice.status IN :statuses " +
           "AND li.invoice.invoiceDate >= :from " +
           "AND li.invoice.invoiceDate <= :to " +
           "GROUP BY EXTRACT(MONTH FROM li.invoice.invoiceDate) " +
           "ORDER BY EXTRACT(MONTH FROM li.invoice.invoiceDate)")
    List<Object[]> monthlyRevenueBetween(
            @Param("statuses") Collection<InvoiceStatus> statuses,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Returns {@link InvoiceLineItem} entities (with invoice, patient, and
     * createdByUser already fetched) for the drill-down detail view.
     * Staff is intentionally left as a lazy association so the caller can
     * access it within a @Transactional context without a chained LEFT JOIN.
     */
    @Query("SELECT li FROM InvoiceLineItem li " +
           "JOIN FETCH li.invoice i " +
           "JOIN FETCH i.patient p " +
           "LEFT JOIN FETCH i.createdByUser u " +
           "WHERE i.status IN :statuses " +
           "AND i.invoiceDate >= :from " +
           "AND i.invoiceDate <= :to " +
           "AND li.category = :category " +
           "ORDER BY i.invoiceDate DESC")
    List<InvoiceLineItem> findLineItemsWithDetails(
            @Param("statuses") Collection<InvoiceStatus> statuses,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("category") LineItemCategory category);

    /**
     * Returns [patientId (UUID), totalBilled (BigDecimal), invoiceCount (Long)]
     * for all non-voided invoices, grouped by patient.
     * Patients with no invoices are NOT included — merge with the full patient
     * list in the service/controller to show zero rows.
     */
    @Query("SELECT i.patient.id, SUM(i.totalAmount), COUNT(i) " +
           "FROM Invoice i " +
           "WHERE i.status != com.adags.hospital.domain.billing.InvoiceStatus.VOIDED " +
           "GROUP BY i.patient.id")
    List<Object[]> patientSpendingTotals();

    // ── Cancellation queries ─────────────────────────────────────────────

    /** Finds all invoices with CANCELLATION_PENDING status, eagerly loading
     *  patient and the requesting user in a single query. */
    @Query("SELECT i FROM Invoice i " +
           "JOIN FETCH i.patient " +
           "LEFT JOIN FETCH i.cancellationRequestedBy " +
           "WHERE i.status = :status " +
           "ORDER BY i.cancellationRequestedAt DESC")
    List<Invoice> findAllByStatusFetchDetails(@Param("status") InvoiceStatus status);

    long countByStatus(InvoiceStatus status);
}
