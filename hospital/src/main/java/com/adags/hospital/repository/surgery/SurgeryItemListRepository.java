package com.adags.hospital.repository.surgery;

import com.adags.hospital.domain.surgery.SurgeryItemList;
import com.adags.hospital.domain.surgery.SurgeryItemListType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SurgeryItemListRepository extends JpaRepository<SurgeryItemList, UUID> {

    List<SurgeryItemList> findBySurgeryOrderIdAndItemType(UUID surgeryOrderId, SurgeryItemListType itemType);

    List<SurgeryItemList> findBySurgeryOrderId(UUID surgeryOrderId);

    List<SurgeryItemList> findBySurgeryOrderIdAndDispensedFalse(UUID surgeryOrderId);

    /** Fetches all items for a surgery order with the invoice eagerly loaded (for payment status). */
    @Query("SELECT s FROM SurgeryItemList s LEFT JOIN FETCH s.invoice WHERE s.surgeryOrder.id = :orderId")
    List<SurgeryItemList> findBySurgeryOrderIdFetchInvoice(@Param("orderId") UUID orderId);

    /**
     * Pharmacy items ready to dispense — not dispensed, and either:
     *   (a) has a direct invoice that is PAID/PARTIALLY_PAID, OR
     *   (b) has no direct invoice but the surgery order's main invoice is PAID/PARTIALLY_PAID
     *       (covers legacy items added before the invoice_id column existed).
     */
    @Query("SELECT s FROM SurgeryItemList s " +
           "JOIN FETCH s.surgeryOrder so JOIN FETCH so.patient " +
           "LEFT JOIN FETCH s.invoice inv " +
           "WHERE s.isLabItem = false AND s.dispensed = false " +
           "AND (" +
           "  (inv IS NOT NULL AND inv.status IN " +
           "    (com.adags.hospital.domain.billing.InvoiceStatus.PAID, " +
           "     com.adags.hospital.domain.billing.InvoiceStatus.PARTIALLY_PAID)) " +
           "  OR " +
           "  (inv IS NULL AND EXISTS (" +
           "    SELECT i FROM com.adags.hospital.domain.billing.Invoice i " +
           "    WHERE i.id = so.surgeryInvoiceId " +
           "    AND i.status IN (" +
           "      com.adags.hospital.domain.billing.InvoiceStatus.PAID, " +
           "      com.adags.hospital.domain.billing.InvoiceStatus.PARTIALLY_PAID))) " +
           ") " +
           "ORDER BY s.createdAt ASC")
    List<SurgeryItemList> findPaidPendingPharmacyQueue();

    /**
     * Pharmacy items awaiting payment — not dispensed, and neither condition above is met.
     */
    @Query("SELECT s FROM SurgeryItemList s " +
           "JOIN FETCH s.surgeryOrder so JOIN FETCH so.patient " +
           "LEFT JOIN FETCH s.invoice inv " +
           "WHERE s.isLabItem = false AND s.dispensed = false " +
           "AND NOT (" +
           "  (inv IS NOT NULL AND inv.status IN " +
           "    (com.adags.hospital.domain.billing.InvoiceStatus.PAID, " +
           "     com.adags.hospital.domain.billing.InvoiceStatus.PARTIALLY_PAID)) " +
           "  OR " +
           "  (inv IS NULL AND EXISTS (" +
           "    SELECT i FROM com.adags.hospital.domain.billing.Invoice i " +
           "    WHERE i.id = so.surgeryInvoiceId " +
           "    AND i.status IN (" +
           "      com.adags.hospital.domain.billing.InvoiceStatus.PAID, " +
           "      com.adags.hospital.domain.billing.InvoiceStatus.PARTIALLY_PAID))) " +
           ") " +
           "ORDER BY s.createdAt ASC")
    List<SurgeryItemList> findAwaitingPaymentPharmacyQueue();
}
